package com.artemkhateev.finance.data

import android.annotation.SuppressLint
import android.content.Context
import com.artemkhateev.finance.data.auth.AuthRepository
import com.artemkhateev.finance.data.demo.DemoFinanceRepository
import com.artemkhateev.finance.data.firebase.CloudFinanceRepository
import com.artemkhateev.finance.data.firebase.FirebaseAuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Единственное место, где выбираются реализации: облако, если сборка знает проект Firebase, иначе демо. */
object AppGraph {

    // Здесь только applicationContext, он живёт столько же, сколько процесс.
    @SuppressLint("StaticFieldLeak")
    private lateinit var context: Context

    /** Для операций, которые должны пережить закрытый экран: выход из аккаунта, импорт. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /** Firebase инициализирован, только если в сборку попал google-services.json. */
    val cloudEnabled: Boolean by lazy { FirebaseApp.getApps(context).isNotEmpty() }

    val auth: AuthRepository? by lazy {
        if (cloudEnabled) FirebaseAuthRepository(context, FirebaseAuth.getInstance(), webClientId()) else null
    }

    val cloud: CloudFinanceRepository? by lazy {
        auth?.let { CloudFinanceRepository(it, FirebaseFirestore.getInstance(), appScope) }
    }

    val repository: FinanceRepository by lazy { cloud ?: DemoFinanceRepository() }

    /** Настройки интерфейса на устройстве: и в облачном, и в демо-режиме. */
    val settings: DeviceSettings by lazy { DeviceSettings(context.getSharedPreferences("settings", Context.MODE_PRIVATE)) }

    @SuppressLint("DiscouragedApi")
    private fun webClientId(): String? {
        // Ресурс генерирует плагин google-services, если в проекте Firebase включён вход через Google.
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id) else null
    }
}
