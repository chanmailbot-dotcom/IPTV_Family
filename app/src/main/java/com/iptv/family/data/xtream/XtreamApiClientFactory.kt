package com.iptv.family.data.xtream

import com.iptv.family.data.xtream.XtreamApiClient

class XtreamApiClientFactory {
    fun create(
        baseUrl: String,
        username: String,
        password: String
    ): XtreamApiClient {
        return XtreamApiClient(baseUrl, username, password)
    }
}