/*
 * Copyright (C) 2019. Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sweers.catchup.service.dribbble

import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.multibindings.IntoMap
import dev.zacsweers.catchup.appconfig.AppConfig
import io.reactivex.rxjava3.core.Single
import io.sweers.catchup.libraries.retrofitconverters.DecodingConverter
import io.sweers.catchup.libraries.retrofitconverters.delegatingCallFactory
import io.sweers.catchup.service.api.CatchUpItem
import io.sweers.catchup.service.api.DataRequest
import io.sweers.catchup.service.api.DataResult
import io.sweers.catchup.service.api.ImageInfo
import io.sweers.catchup.service.api.Mark.Companion.createCommentMark
import io.sweers.catchup.service.api.Service
import io.sweers.catchup.service.api.ServiceIndex
import io.sweers.catchup.service.api.ServiceKey
import io.sweers.catchup.service.api.ServiceMeta
import io.sweers.catchup.service.api.ServiceMetaIndex
import io.sweers.catchup.service.api.ServiceMetaKey
import io.sweers.catchup.service.api.VisualService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
private annotation class InternalApi

private const val SERVICE_KEY = "dribbble"

@ServiceKey(SERVICE_KEY)
@ContributesMultibinding(ServiceIndex::class, boundType = Service::class)
class DribbbleService @Inject constructor(
  @InternalApi private val serviceMeta: ServiceMeta,
  private val api: DribbbleApi
) :
  VisualService {

  override fun meta() = serviceMeta

  override fun fetchPage(request: DataRequest): Single<DataResult> {
    val page = request.pageId.toInt()
    return api.getPopular(page, 50)
      .flattenAsObservable { it }
      .map {
        CatchUpItem(
          id = it.id,
          title = "",
          score = "+" to it.likesCount.toInt(),
          timestamp = it.createdAt,
          author = "/u/" + it.user.name,
          source = null,
          tag = null,
          itemClickUrl = it.images.best(),
          imageInfo = ImageInfo(
            it.images.normal,
            it.images.best(true),
            it.animated,
            it.htmlUrl,
            it.images.bestSize(),
            it.id.toString()
          ),
          mark = createCommentMark(count = it.commentsCount.toInt())
        )
      }
      .toList()
      .map { DataResult(it, (page + 1).toString()) }
  }
}

@ContributesTo(ServiceMetaIndex::class)
@Module
abstract class DribbbleMetaModule {

  @IntoMap
  @ServiceMetaKey(SERVICE_KEY)
  @Binds
  internal abstract fun dribbbleServiceMeta(@InternalApi meta: ServiceMeta): ServiceMeta

  companion object {

    @InternalApi
    @Provides
    @Reusable
    internal fun provideDribbbleServiceMeta(): ServiceMeta = ServiceMeta(
      SERVICE_KEY,
      R.string.dribbble,
      R.color.dribbbleAccent,
      R.drawable.logo_dribbble,
      isVisual = true,
      pagesAreNumeric = true,
      firstPageKey = "1"
    )
  }
}

@ContributesTo(ServiceIndex::class)
@Module(includes = [DribbbleMetaModule::class])
object DribbbleModule {
  @Provides
  internal fun provideDribbbleService(
    client: Lazy<OkHttpClient>,
    rxJavaCallAdapterFactory: RxJava3CallAdapterFactory,
    appConfig: AppConfig
  ): DribbbleApi {
    return Retrofit.Builder().baseUrl(DribbbleApi.ENDPOINT)
      .delegatingCallFactory(client)
      .addCallAdapterFactory(rxJavaCallAdapterFactory)
      .addConverterFactory(DecodingConverter.newFactory(DribbbleParser::parse))
      .validateEagerly(appConfig.isDebug)
      .build()
      .create(DribbbleApi::class.java)
  }
}
