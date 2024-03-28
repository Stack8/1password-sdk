package com.ziro.onepassword.sdk;

import java.util.Set;

record Item(String id, String title, String category, Set<Field> fields) {}
