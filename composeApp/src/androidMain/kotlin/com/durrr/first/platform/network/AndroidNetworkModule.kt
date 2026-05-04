package com.durrr.first.platform.network

import com.durrr.first.core.network.AppServerClient

object AndroidNetworkModule {
    fun provideServerClient(): AppServerClient = AppServerClient()
}
