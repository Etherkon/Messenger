package com.westie.messenger

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.size
import java.io.*
import java.lang.Exception
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var handler = Handler(Looper.getMainLooper())
    private lateinit var msgList : LinearLayout
    private lateinit var userInputField : EditText
    private lateinit var clientThread : ClientThread
    private lateinit var thread : Thread
    private var started = false
    private var messageLog : StringBuilder = StringBuilder()
    private var target_ip = "192.168.1.36"
    private var target_port = 3003
    private var name = "Petri"

    private var secretKey: SecretKeySpec? = null
    private lateinit var key: ByteArray
    private val secret = "chiliwestie"
    private val iv = "eBAzNTNhLkQ1Njc4UUFaEK=="

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle("Ryhmä-keskustelu")

        msgList = findViewById<LinearLayout>(R.id.msgList)
        userInputField = findViewById(R.id.user_input)
    }

    private fun textView(message: String) : TextView{
        var message_input = message
        if(null == message || message.trim().isEmpty()) { message_input = "<Empty Message>" }
        var tv = TextView(this)
        tv.setText(message_input)
        return tv
    }

    fun setKey(myKey: String) {
        var sha: MessageDigest? = null
        try {
            key = myKey.toByteArray(charset("UTF-8"))
            sha = MessageDigest.getInstance("SHA-1")
            key = sha.digest(key)
            key = Arrays.copyOf(key, 16)
            secretKey = SecretKeySpec(key, "AES")
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
    }

    fun encryptMessage(message: String): String? {
        try {
            setKey(secret)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            return Base64.getEncoder().encodeToString(cipher.doFinal(message.toByteArray(charset("UTF-8"))))
        } catch (e: Exception){
            e.printStackTrace()
        }
        return null
    }

    fun decryptMessage(message: String): String?{
        try {
            setKey(secret)
            val cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            return String(cipher.doFinal(Base64.getDecoder().decode(message)))
        } catch (e: Exception){
            e.printStackTrace()
        }
        return null
    }


    private fun showMessage(message: String){
        handler.post(Runnable {
            kotlin.run { msgList.addView(textView(message)) }
        })
    }

    fun saveMessage(message: String){
        messageLog.append(message).append(",")
        val save : SharedPreferences = getSharedPreferences("mypref", 0)
        save.edit().putString("messagelog", messageLog.toString()).commit()
    }

    fun loadMessages(){
        val load : SharedPreferences = getSharedPreferences("mypref", 0)
        val receivedMessages = load.getString("messagelog", "The chat retrieved from memory.")
        if(receivedMessages != null){
            val messages = receivedMessages.split(",")
            for(message in messages){
                val decrypted_message = decryptMessage(message)
                if(decrypted_message != null){
                    showMessage(decrypted_message)
                }
            }
        }
    }

    override fun onClick(view: View) {

        if(view.id == R.id.sendButton){
            if(started) {
                val userMessage = userInputField.text.toString().trim()
                showMessage(name + ": " + userMessage)

                val encrypted_message = encryptMessage(name + ": " + userMessage)
                if(encrypted_message != null){
                    if (null != clientThread) {
                        clientThread.sendMessage(encrypted_message)
                    }
                    saveMessage(encrypted_message)
                }
            }
            else {
                clientThread = ClientThread()
                thread = Thread(clientThread)
                thread.start()
                val connect_button = findViewById<Button>(R.id.sendButton)
                connect_button.setText("Send")
                showMessage("You are connected...")
                started = true
                loadMessages()
                return
            }
        }
        if(view.id == R.id.settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        if(view.id == R.id.reset) {
            if(msgList.size > 0){
                msgList.removeAllViews()
                val to_be_removed : SharedPreferences = getSharedPreferences("mypref", 0)
                to_be_removed.edit().clear().commit()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(data != null && (requestCode == requestCode) && (resultCode == Activity.RESULT_OK)){
            if(data.hasExtra("ip")){
                target_ip = data.getStringExtra("ip").toString().trim()
            }
            if(data.hasExtra("port")){
                target_port = data.getStringExtra("port").toString().toInt()
            }
            if(data.hasExtra("name")){
                name = data.getStringExtra("name").toString().trim()
            }
        }
    }

    inner class ClientThread : Runnable {

        private lateinit var socket : Socket
        private lateinit var input: BufferedReader

        override fun run() {
            try {
                val serverAddr : InetAddress = InetAddress.getByName(target_ip)
                socket = Socket(serverAddr, target_port)

                while(!Thread.currentThread().isInterrupted){
                    this.input = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val message = input.readLine()
                    if(null == message || "Disconnect".contentEquals(message)){
                        Thread.interrupted()
                        break
                    }
                    val decrypted_message = decryptMessage(message)
                    if(decrypted_message != null) {
                        showMessage(decrypted_message)
                    }
                    saveMessage(message)
                }
            } catch(ei : UnknownHostException){
                ei.printStackTrace()
            } catch (ei : IOException) {
                ei.printStackTrace()
            }
        }

        fun sendMessage(message : String){
            Thread(Runnable {
                kotlin.run {
                    try {
                        if (null != socket) {
                            val out = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())), true)
                            out.println(message)
                        }
                    }
                    catch (e : Exception){
                        e.printStackTrace()
                    }
                }
            }).start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
