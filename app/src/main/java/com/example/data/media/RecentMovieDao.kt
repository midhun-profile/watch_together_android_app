package com.example.data.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentMovieDao {

    @Query("SELECT * FROM recent_movies ORDER BY lastPlayedTimestamp DESC")
    fun getAllRecentMovies(): Flow<List<RecentMovieEntity>>

    @Query("SELECT * FROM recent_movies WHERE uriString = :uriString LIMIT 1")
    suspend fun getMovie(uriString: String): RecentMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(movie: RecentMovieEntity)

    @Query("DELETE FROM recent_movies WHERE uriString = :uriString")
    suspend fun deleteMovie(uriString: String)

    @Query("DELETE FROM recent_movies")
    suspend fun clearAll()
}
