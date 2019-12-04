package com.apollographql.apollo.kotlinsample

import android.app.Application
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.cache.normalized.CacheKey
import com.apollographql.apollo.cache.normalized.CacheKeyResolver
import com.apollographql.apollo.cache.normalized.sql.ApolloSqlHelper
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.apollo.kotlinsample.data.ApolloCallbackService
import com.apollographql.apollo.kotlinsample.data.ApolloCoroutinesService
import com.apollographql.apollo.kotlinsample.data.ApolloRxService
import com.apollographql.apollo.kotlinsample.data.GitHubDataSource
import okhttp3.*
import okio.Timeout


@Suppress("unused")
class KotlinSampleApp : Application() {
  private val baseUrl = "https://api.github.com/graphql"
  private val apolloClient: ApolloClient by lazy {
    val okHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
          val request = chain.request().newBuilder()
              .addHeader("Authorization", "bearer ${BuildConfig.GITHUB_OAUTH_TOKEN}")
              .build()

          chain.proceed(request)
        }
        .build()

    val apolloSqlHelper = ApolloSqlHelper.create(this, "github_cache")
    val sqlNormalizedCacheFactory = SqlNormalizedCacheFactory(apolloSqlHelper)
    val cacheKeyResolver = object : CacheKeyResolver() {
      override fun fromFieldRecordSet(field: ResponseField, recordSet: MutableMap<String, Any>): CacheKey {
        return if (recordSet["__typename"] == "Repository") {
          CacheKey.from(recordSet["id"] as String)
        } else {
          CacheKey.NO_KEY
        }
      }

      override fun fromFieldArguments(field: ResponseField, variables: Operation.Variables): CacheKey {
        return CacheKey.NO_KEY
      }
    }

    ApolloClient.builder()
        .serverUrl(baseUrl)
        .normalizedCache(sqlNormalizedCacheFactory, cacheKeyResolver)
        .callFactory {
          DecoratedCall(okHttpClient.newCall(it))
        }
        .build()
  }

  class DecoratedCall(val call: Call) : Call {
    override fun enqueue(responseCallback: Callback) {
      call.enqueue(responseCallback)
    }

    override fun isExecuted(): Boolean {
      return call.isExecuted
    }

    override fun timeout(): Timeout {
      return call.timeout()
    }

    override fun clone(): Call {
      return call.clone()
    }

    override fun isCanceled(): Boolean {
      return call.isCanceled
    }

    override fun cancel() {
      call.cancel()
    }

    override fun request(): Request {
      return call.request()
    }

    override fun execute(): Response {
      return call.execute()
    }
  }

  /**
   * Builds an implementation of [GitHubDataSource]. To configure which one is returned, just comment out the appropriate
   * lines.
   *
   * @param[serviceTypes] Modify the type of service we use, if we want. To use the same thing across the board simply update
   * it here.
   */
  fun getDataSource(serviceTypes: ServiceTypes = ServiceTypes.CALLBACK): GitHubDataSource {
    return when (serviceTypes) {
      ServiceTypes.CALLBACK -> ApolloCallbackService(apolloClient)
      ServiceTypes.RX_JAVA -> ApolloRxService(apolloClient)
      ServiceTypes.COROUTINES -> ApolloCoroutinesService(apolloClient)
    }
  }

  enum class ServiceTypes {
    CALLBACK,
    RX_JAVA,
    COROUTINES
  }
}