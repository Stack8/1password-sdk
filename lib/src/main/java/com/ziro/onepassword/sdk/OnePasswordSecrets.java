package com.ziro.onepassword.sdk;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ziro.espresso.fluent.exceptions.SystemUnhandledException;
import com.ziro.espresso.javax.annotation.extensions.NonNullByDefault;
import com.ziro.espresso.okhttp3.OkHttpClientFactory;
import com.ziro.espresso.okhttp3.SynchronousCallAdapterFactory;
import com.ziro.espresso.streams.MoreCollectors;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Value
@NonNullByDefault
public class OnePasswordSecrets {

    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofSeconds(30);
    private static final String ENV_VAR_TEST_ENV_ONE_PASSWORD_API_ACCESS_TOKEN =
            "TEST_ENV_ONE_PASSWORD_API_ACCESS_TOKEN";

    String baseUrl;

    @Getter(AccessLevel.PRIVATE)
    @ToString.Exclude
    OnePasswordConnectServerApiClient client;

    @Builder
    public OnePasswordSecrets(String baseUrl, String accessToken, @Nullable X509TrustManager trustManager) {
        Preconditions.checkArgument(Strings.isNullOrEmpty(baseUrl), "baseUrl is required.");
        this.baseUrl = baseUrl;
        Preconditions.checkArgument(Strings.isNullOrEmpty(accessToken), "accessToken is required.");
        client = createClient(
                baseUrl,
                System.getenv().getOrDefault(ENV_VAR_TEST_ENV_ONE_PASSWORD_API_ACCESS_TOKEN, accessToken),
                Objects.requireNonNullElseGet(trustManager, OkHttpClientFactory::createNaiveX509TrustManager));
    }

    private static OnePasswordConnectServerApiClient createClient(
            String baseUrl, String accessToken, X509TrustManager trustManager) {
        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .setPrettyPrinting()
                .create();

        Retrofit.Builder retrofitBuilder = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(new SynchronousCallAdapterFactory<>());

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        okHttpClientBuilder
                .connectTimeout(DEFAULT_CONNECTION_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
                .hostnameVerifier((s, sslSession) -> s.equalsIgnoreCase(sslSession.getPeerHost()));

        SSLSocketFactory sslSocketFactory = OkHttpClientFactory.buildSocketFactory(trustManager);
        okHttpClientBuilder.sslSocketFactory(sslSocketFactory, trustManager);
        okHttpClientBuilder.addInterceptor(chain -> chain.proceed(addRequiredHeaders(chain.request(), accessToken)));
        OkHttpClient okHttpClient = okHttpClientBuilder.build();
        Retrofit retrofit = retrofitBuilder.client(okHttpClient).build();
        return retrofit.create(OnePasswordConnectServerApiClient.class);
    }

    private static Request addRequiredHeaders(Request request, String accessToken) {
        return request.newBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", String.format("Bearer %s", accessToken))
                .build();
    }

    public Map<Object, Object> getSecureNoteAsMap(String vaultId, String itemId) {
        Item item = client.getItem(vaultId, itemId);
        Field notesPlain = item.fields().stream()
                .filter(field -> "notesPlain".equals(field.label()))
                .collect(MoreCollectors.exactlyOne("field with [label=notesPlain]"));
        Properties properties = new Properties();
        InputStream in = new ByteArrayInputStream(notesPlain.value().getBytes());
        try {
            properties.load(in);
        } catch (IOException e) {
            throw SystemUnhandledException.fluent()
                    .message(
                            "Something went wrong while trying to load secure note as properties using "
                                    + "[vaultId=%s, itemId=%s].",
                            vaultId, itemId)
                    .cause(e)
                    .exception();
        }
        return properties;
    }
}
