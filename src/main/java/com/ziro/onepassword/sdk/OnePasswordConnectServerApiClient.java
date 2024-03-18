package com.ziro.onepassword.sdk;

import retrofit2.http.GET;
import retrofit2.http.Path;

interface OnePasswordConnectServerApiClient {

    @GET("/v1/vaults/{vaultId}/items/{itemId}")
    Item getItem(@Path("vaultId") String vaultId, @Path("itemId") String itemId);
}
