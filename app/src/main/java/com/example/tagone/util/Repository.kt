package com.example.tagone.util

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.downloader.Error
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import com.downloader.PRDownloaderConfig
import com.example.tagone.database.PostsDatabase
import com.example.tagone.database.toDisplayModel
import com.example.tagone.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

class PostsRepository(private val postsDatabase: PostsDatabase) {

    val favouritesList = Transformations.map(postsDatabase.postsDao.getByDate()) {
        it.toDisplayModel()
    }

    var remainingItemsInDownload = 0

    private val _searchList = MutableLiveData<List<DisplayModel>>()
    val searchList: LiveData<List<DisplayModel>>
        get() = _searchList

    private val _tagList = MutableLiveData<List<DanbooruTagNet>>()
    val tagList: LiveData<List<DanbooruTagNet>>
        get() = _tagList

    private val _singlePost = MutableLiveData<DisplayModel>()
    val singlePost: LiveData<DisplayModel>
        get() = _singlePost

    /**
     * Gets list of tags matching start of input
     */
    suspend fun getTagsFromNetwork(tag: String) {
        _tagList.value = DanbooruApi.danbooruService.getTags(10, 1, "$tag*", "count", true)
    }

    /**
     * Favourites database manipulation
     */
    suspend fun addToFavourites(post: DisplayModel, date: String) {
        withContext(Dispatchers.IO) {
            postsDatabase.postsDao.addToFavourites(post.toFavouritesDatabaseFormat(date))
        }
    }

    suspend fun removeFromFavourites(post: DisplayModel) {
        withContext(Dispatchers.IO) {
            postsDatabase.postsDao.removeFromFavourites(post.toFavouritesDatabaseFormat(post.dateFavourited))
        }
    }

    fun isFavourited(fileUrl: String): LiveData<Boolean> {
        return postsDatabase.postsDao.isInFavourites(fileUrl)
    }

    /**
     * Tag search network retrieval functions
     */
    var searchLimit = 100

    suspend fun getPostsFromNetwork(server: Int, tags: String, page: Int) {
        val encodedTags = tags.replace(" ", "+")
        try {
            when (server) {
                0 -> _searchList.value =
                    DanbooruApi.danbooruService.getPosts(encodedTags, searchLimit, page).danbooruToDisplayModel()
                1 -> _searchList.value =
                    GelbooruApi.gelbooruService.getPosts(encodedTags, searchLimit, page).postList.gelbooruToDisplayModel()
            }
        } catch (e: HttpException) {
            Log.i("test","Post limit decreased")
            searchLimit -= 20
            getPostsFromNetwork(server, tags, page)
        }

    }

    suspend fun addPostsFromNetwork(server: Int, tags: String, page: Int) {
        val encodedTags = tags.replace(" ", "+")
        _searchList.value = _searchList.value?.plus(
            when (server) {
                0 -> DanbooruApi.danbooruService.getPosts(encodedTags, searchLimit, page)
                    .danbooruToDisplayModel()
                else -> GelbooruApi.gelbooruService.getPosts(encodedTags, searchLimit, page)
                    .postList.gelbooruToDisplayModel()
            }
        )
    }

    suspend fun getSinglePostFromNetwork(post: DisplayModel) {
        val server = post.domain
        val id = post.id
        _singlePost.value = when (server) {
            Constants.DANBOORU -> DanbooruApi.danbooruService.getSinglePost(id).danbooruToDisplayModel()
            else -> GelbooruApi.gelbooruService.getSinglePost(id).post?.gelbooruToDisplayModel()
        }
    }

    fun clearSinglePost() {
        _singlePost.value = null
    }

    /**
     * Function to download all favourites (avoids downloading duplicates)
     */
    fun downloadAllFavourites(application: Application, directoryString: String) {
        val config = PRDownloaderConfig.newBuilder()
            .setDatabaseEnabled(true)
            .build()
        PRDownloader.initialize(application.applicationContext, config)

        // Getting names of files already existing in save directory
        val directory = File(directoryString)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val files = directory.list()
        files.forEach { Log.i("test", it) }

        // Iterating through list of posts in favourites list
        if (!favouritesList.value.isNullOrEmpty()) {
            for (post in favouritesList.value!!) {
                val fileName = createFileName(post.fileUrl!!)
                if (fileName !in files) {
                    Log.i("test", fileName)
                    Log.i("test", "Download initiated: ${post.fileUrl}")
                    downloadFile(post.fileUrl, directoryString, fileName)
                    remainingItemsInDownload++
                }
            }
        }
    }

    /**
     * Individual file download
     */
    private fun downloadFile(fileUrl: String?, directoryString: String?, fileName: String?): Int {
        val id = PRDownloader.download(
            fileUrl,
            directoryString,
            fileName
        )
            .build()
            .start(DownloadListener())
        Log.i("test", "id: $id")
        return id
    }

    inner class DownloadListener : OnDownloadListener {
        override fun onDownloadComplete() {
            Log.i("test", "Download complete")
            remainingItemsInDownload--
            return
        }

        override fun onError(error: Error?) {
            Log.i("test", error.toString())
            remainingItemsInDownload--
            return
        }
    }
}