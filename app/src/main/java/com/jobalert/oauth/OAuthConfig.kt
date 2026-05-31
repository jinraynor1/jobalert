package com.jobalert.oauth

data class OAuthConfig(
    val authEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val scope: String,
    val host: String,
    val port: Int,
    val useSsl: Boolean
)
