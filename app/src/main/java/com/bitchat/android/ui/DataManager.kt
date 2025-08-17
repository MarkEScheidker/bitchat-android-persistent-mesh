package com.bitchat.android.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.bitchat.android.model.BitchatMessage
import kotlin.random.Random

/**
 * Handles data persistence operations for the chat system
 */
class DataManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DataManager"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences("bitchat_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Channel-related maps that need to persist state
    private val _channelCreators = mutableMapOf<String, String>()
    private val _favoritePeers = mutableSetOf<String>()
    private val _blockedUsers = mutableSetOf<String>()
    private val _channelMembers = mutableMapOf<String, MutableSet<String>>()
    
    val channelCreators: Map<String, String> get() = _channelCreators
    val favoritePeers: Set<String> get() = _favoritePeers
    val blockedUsers: Set<String> get() = _blockedUsers
    val channelMembers: Map<String, MutableSet<String>> get() = _channelMembers
    
    // MARK: - Nickname Management
    
    fun loadNickname(): String {
        val savedNickname = prefs.getString("nickname", null)
        return if (savedNickname != null) {
            savedNickname
        } else {
            val randomNickname = "anon${Random.nextInt(1000, 9999)}"
            saveNickname(randomNickname)
            randomNickname
        }
    }
    
    fun saveNickname(nickname: String) {
        prefs.edit().putString("nickname", nickname).apply()
    }

    // MARK: - Background Preferences

    fun isPersistentNetworkEnabled(): Boolean = prefs.getBoolean("persistent_network", false)

    fun setPersistentNetworkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("persistent_network", enabled).apply()
        if (!enabled) {
            setStartOnBootEnabled(false)
        }
    }

    fun isStartOnBootEnabled(): Boolean = prefs.getBoolean("start_on_boot", false)

    fun setStartOnBootEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("start_on_boot", enabled).apply()
    }

    // MARK: - Pending Private Messages

    fun savePendingPrivateMessage(message: BitchatMessage) {
        val messages = loadPendingPrivateMessages().toMutableList()
        messages.add(message)
        val json = gson.toJson(messages)
        prefs.edit().putString("pending_private_messages", json).apply()
    }

    fun loadPendingPrivateMessages(): List<BitchatMessage> {
        val json = prefs.getString("pending_private_messages", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<BitchatMessage>>() {}.type
            gson.fromJson<List<BitchatMessage>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearPendingPrivateMessages() {
        prefs.edit().remove("pending_private_messages").apply()
    }
    
    // MARK: - Channel Data Management
    
    fun loadChannelData(): Pair<Set<String>, Set<String>> {
        // Load joined channels
        val savedChannels = prefs.getStringSet("joined_channels", emptySet()) ?: emptySet()
        
        // Load password protected channels
        val savedProtectedChannels = prefs.getStringSet("password_protected_channels", emptySet()) ?: emptySet()
        
        // Load channel creators
        val creatorsJson = prefs.getString("channel_creators", "{}")
        try {
            val creatorsMap = gson.fromJson(creatorsJson, Map::class.java) as? Map<String, String>
            creatorsMap?.let { _channelCreators.putAll(it) }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        
        // Initialize channel members for loaded channels
        savedChannels.forEach { channel ->
            if (!_channelMembers.containsKey(channel)) {
                _channelMembers[channel] = mutableSetOf()
            }
        }
        
        return Pair(savedChannels, savedProtectedChannels)
    }
    
    fun saveChannelData(joinedChannels: Set<String>, passwordProtectedChannels: Set<String>) {
        prefs.edit().apply {
            putStringSet("joined_channels", joinedChannels)
            putStringSet("password_protected_channels", passwordProtectedChannels)
            putString("channel_creators", gson.toJson(_channelCreators))
            apply()
        }
    }
    
    fun addChannelCreator(channel: String, creatorID: String) {
        _channelCreators[channel] = creatorID
    }
    
    fun removeChannelCreator(channel: String) {
        _channelCreators.remove(channel)
    }
    
    fun isChannelCreator(channel: String, peerID: String): Boolean {
        return _channelCreators[channel] == peerID
    }
    
    // MARK: - Channel Members Management
    
    fun addChannelMember(channel: String, peerID: String) {
        if (!_channelMembers.containsKey(channel)) {
            _channelMembers[channel] = mutableSetOf()
        }
        _channelMembers[channel]?.add(peerID)
    }
    
    fun removeChannelMember(channel: String, peerID: String) {
        _channelMembers[channel]?.remove(peerID)
    }
    
    fun removeChannelMembers(channel: String) {
        _channelMembers.remove(channel)
    }
    
    fun cleanupDisconnectedMembers(channel: String, connectedPeers: List<String>, myPeerID: String) {
        _channelMembers[channel]?.removeAll { memberID ->
            memberID != myPeerID && !connectedPeers.contains(memberID)
        }
    }
    
    fun cleanupAllDisconnectedMembers(connectedPeers: List<String>, myPeerID: String) {
        _channelMembers.values.forEach { members ->
            members.removeAll { memberID ->
                memberID != myPeerID && !connectedPeers.contains(memberID)
            }
        }
    }
    
    // MARK: - Favorites Management
    
    fun loadFavorites() {
        val savedFavorites = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _favoritePeers.addAll(savedFavorites)
        Log.d(TAG, "Loaded ${savedFavorites.size} favorite users from storage: $savedFavorites")
    }
    
    fun saveFavorites() {
        prefs.edit().putStringSet("favorites", _favoritePeers).apply()
        Log.d(TAG, "Saved ${_favoritePeers.size} favorite users to storage: $_favoritePeers")
    }
    
    fun addFavorite(fingerprint: String) {
        val wasAdded = _favoritePeers.add(fingerprint)
        Log.d(TAG, "addFavorite: fingerprint=$fingerprint, wasAdded=$wasAdded")
        saveFavorites()
        logAllFavorites()
    }
    
    fun removeFavorite(fingerprint: String) {
        val wasRemoved = _favoritePeers.remove(fingerprint)
        Log.d(TAG, "removeFavorite: fingerprint=$fingerprint, wasRemoved=$wasRemoved")
        saveFavorites()
        logAllFavorites()
    }
    
    fun isFavorite(fingerprint: String): Boolean {
        val result = _favoritePeers.contains(fingerprint)
        Log.d(TAG, "isFavorite check: fingerprint=$fingerprint, result=$result")
        return result
    }
    
    fun logAllFavorites() {
        Log.i(TAG, "=== ALL FAVORITE USERS ===")
        Log.i(TAG, "Total favorites: ${_favoritePeers.size}")
        _favoritePeers.forEach { fingerprint ->
            Log.i(TAG, "Favorite fingerprint: $fingerprint")
        }
        Log.i(TAG, "========================")
    }
    
    // MARK: - Blocked Users Management
    
    fun loadBlockedUsers() {
        val savedBlockedUsers = prefs.getStringSet("blocked_users", emptySet()) ?: emptySet()
        _blockedUsers.addAll(savedBlockedUsers)
    }
    
    fun saveBlockedUsers() {
        prefs.edit().putStringSet("blocked_users", _blockedUsers).apply()
    }
    
    fun addBlockedUser(fingerprint: String) {
        _blockedUsers.add(fingerprint)
        saveBlockedUsers()
    }
    
    fun removeBlockedUser(fingerprint: String) {
        _blockedUsers.remove(fingerprint)
        saveBlockedUsers()
    }
    
    fun isUserBlocked(fingerprint: String): Boolean {
        return _blockedUsers.contains(fingerprint)
    }
    
    // MARK: - Emergency Clear
    
    fun clearAllData() {
        _channelCreators.clear()
        _favoritePeers.clear()
        _blockedUsers.clear()
        _channelMembers.clear()
        prefs.edit().clear().apply()
    }
}
