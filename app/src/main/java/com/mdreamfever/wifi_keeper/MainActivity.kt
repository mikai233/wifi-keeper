package com.mdreamfever.wifi_keeper

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope(),
    AdapterView.OnItemSelectedListener {
    private val tag = MainActivity::class.java.name
    private lateinit var serviceBinder: Keeper.ServiceBinder
    private var ispIndex: Int = 0
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            serviceBinder = service as Keeper.ServiceBinder
            val log = findViewById<TextView>(R.id.logView)
            log.movementMethod = ScrollingMovementMethod.getInstance()
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
            serviceBinder.registerCallback {
                log.append("${formatter.format(Date())} : ${it.toString()}\n")
            }
            getAccountInfo()?.let {
                serviceBinder.login(it)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        configSpinner()
        setInputText()
        configButtonListener()
        startKeeperService()
        title = "WIFI Keeper"
        findViewById<TextView>(R.id.logView).apply {
            append("提示：如果你要长期运行本软件，请不要将本软件加入电池优化名单之中（大部分手机默认加入），电池优化会导致软件运行不正常\n")
            append("软件需要保持后台才能正常监控WIFI\n")
            append("如果你不需要软件保持后台，那么请在连接WIFI之后杀死本应用\n\n")
        }
        clearLogTask()
        ignoreBatteryOptimization()
    }

    private fun ignoreBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName).takeIf { !it }?.let {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName");
                    startActivity(this);
                }
            }
        }
    }

    private fun clearLogTask() {
        launch {
            while (true) {
                delay(60 * 60 * 1000)
                findViewById<TextView>(R.id.logView).text = ""
            }
        }
    }

    private fun setInputText() {
        val prefs = getSharedPreferences("account", Context.MODE_PRIVATE)
        prefs.getString("username", null)?.let {
            val username: TextInputEditText = findViewById(R.id.username)
            username.setText(it)
        }
        prefs.getString("password", null)?.let {
            val password: TextInputEditText = findViewById(R.id.password)
            password.setText(it)
        }
        prefs.getInt("isp", 1).let {
            val spinner: Spinner = findViewById(R.id.planets_spinner)
            spinner.setSelection(it)
            ispIndex = it
        }
    }

    private fun configButtonListener() {
        val loginButton: Button = findViewById(R.id.login)
        loginButton.setOnClickListener {
            val account = getAccountInfo()
            if (account == null) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
                Log.i(tag, "account info $account")
            } else {
                serviceBinder.login(account)
                saveAccountInfo()
            }
        }
        val logoutButton: Button = findViewById(R.id.logout)
        logoutButton.setOnClickListener {
            launch {
                serviceBinder.logout()
            }
        }
    }

    private fun configSpinner() {
        val spinner: Spinner = findViewById(R.id.planets_spinner)
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            Domain.values().map { it.ISPName }).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }
        spinner.onItemSelectedListener = this
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    private fun startKeeperService() {
        Intent(this, Keeper::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun getAccountInfo(): LoginInfo? {
        val usernameText: TextInputEditText = findViewById(R.id.username)
        val username = usernameText.text.toString()
        val passwordText: TextInputEditText = findViewById(R.id.password)
        val password = passwordText.text.toString()
        return if (username.isEmpty() || password.isEmpty()) null
        else {
            val domain = Domain.values()[ispIndex]
            LoginInfo(
                username,
                domain.value,
                Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP),
                domain.enableMacAuth
            )
        }
    }

    private fun saveAccountInfo() {
        getSharedPreferences("account", Context.MODE_PRIVATE).edit(true) {
            val username: TextInputEditText = findViewById(R.id.username)
            val password: TextInputEditText = findViewById(R.id.password)
            putString("username", username.text.toString())
            putString("password", password.text.toString())
            putInt("isp", ispIndex)
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        ispIndex = position
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}