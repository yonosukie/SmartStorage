package com.smartstorage.app.data

import androidx.room.*

// Typed IDs and foreign keys protect relations; payloads use the versioned domain serialization.
@Entity(tableName = "places") data class PlaceRecord(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "items") data class ItemRecord(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "batches", foreignKeys = [ForeignKey(entity = ItemRecord::class, parentColumns = ["id"], childColumns = ["itemId"])], indices = [Index("itemId")])
data class BatchRecord(@PrimaryKey val id: String, val itemId: String, val expires: String?, val payload: String)
@Entity(tableName = "balances", foreignKeys = [
    ForeignKey(entity = BatchRecord::class, parentColumns = ["id"], childColumns = ["batchId"]),
    ForeignKey(entity = PlaceRecord::class, parentColumns = ["id"], childColumns = ["placeId"])],
    indices = [Index(value = ["batchId", "placeId"], unique = true), Index("placeId")])
data class BalanceRecord(@PrimaryKey val id: String, val batchId: String, val placeId: String, val quantity: Int, val revision: Int)
@Entity(tableName = "movements", foreignKeys = [ForeignKey(entity = BatchRecord::class, parentColumns = ["id"], childColumns = ["batchId"])], indices = [Index("batchId")])
data class MovementRecord(@PrimaryKey val id: String, val batchId: String, val payload: String)
@Entity(tableName = "labels") data class LabelRecord(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "layouts") data class LayoutRecord(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "packing") data class PackingRecord(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "metadata") data class MetaRecord(@PrimaryKey val id: String, val payload: String)

@Dao interface StorageDao {
    @Query("SELECT * FROM places") suspend fun places(): List<PlaceRecord>
    @Query("SELECT * FROM items") suspend fun items(): List<ItemRecord>
    @Query("SELECT * FROM batches") suspend fun batches(): List<BatchRecord>
    @Query("SELECT * FROM balances") suspend fun balances(): List<BalanceRecord>
    @Query("SELECT * FROM movements") suspend fun movements(): List<MovementRecord>
    @Query("SELECT * FROM labels") suspend fun labels(): List<LabelRecord>
    @Query("SELECT * FROM layouts") suspend fun layouts(): List<LayoutRecord>
    @Query("SELECT * FROM packing") suspend fun packing(): List<PackingRecord>
    @Query("SELECT * FROM metadata") suspend fun metadata(): List<MetaRecord>
    @Upsert suspend fun places(rows: List<PlaceRecord>)
    @Upsert suspend fun items(rows: List<ItemRecord>)
    @Upsert suspend fun batches(rows: List<BatchRecord>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun balances(rows: List<BalanceRecord>)
    @Update suspend fun updateBalances(rows: List<BalanceRecord>)
    @Upsert suspend fun movements(rows: List<MovementRecord>)
    @Upsert suspend fun labels(rows: List<LabelRecord>)
    @Upsert suspend fun layouts(rows: List<LayoutRecord>)
    @Upsert suspend fun packing(rows: List<PackingRecord>)
    @Upsert suspend fun metadata(rows: List<MetaRecord>)
    @Query("DELETE FROM movements WHERE id NOT IN (:ids)") suspend fun pruneMovements(ids: List<String>)
    @Query("DELETE FROM balances WHERE id NOT IN (:ids)") suspend fun pruneBalances(ids: List<String>)
    @Query("DELETE FROM batches WHERE id NOT IN (:ids)") suspend fun pruneBatches(ids: List<String>)
    @Query("DELETE FROM items WHERE id NOT IN (:ids)") suspend fun pruneItems(ids: List<String>)
    @Query("DELETE FROM places WHERE id NOT IN (:ids)") suspend fun prunePlaces(ids: List<String>)
    @Query("DELETE FROM labels WHERE id NOT IN (:ids)") suspend fun pruneLabels(ids: List<String>)
    @Query("DELETE FROM layouts WHERE id NOT IN (:ids)") suspend fun pruneLayouts(ids: List<String>)
    @Query("DELETE FROM packing WHERE id NOT IN (:ids)") suspend fun prunePacking(ids: List<String>)
    @Query("DELETE FROM places WHERE id IN (:ids)") suspend fun deletePlaces(ids: List<String>)
    @Query("DELETE FROM labels WHERE id IN (:ids)") suspend fun deleteLabels(ids: List<String>)
    @Query("DELETE FROM layouts WHERE id IN (:ids)") suspend fun deleteLayouts(ids: List<String>)
    @Query("DELETE FROM packing WHERE id IN (:ids)") suspend fun deletePacking(ids: List<String>)
}

@Database(entities = [PlaceRecord::class, ItemRecord::class, BatchRecord::class, BalanceRecord::class,
    MovementRecord::class, LabelRecord::class, LayoutRecord::class, PackingRecord::class, MetaRecord::class], version = 1, exportSchema = true)
abstract class StorageDatabase : RoomDatabase() { abstract fun dao(): StorageDao }
