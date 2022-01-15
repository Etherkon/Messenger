package com.westie.messenger_server

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.size
import java.io.*
import java.lang.StringBuilder
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity(), View.OnClickListener {

    val SERVER_PORT = 3003
    private var handler = Handler(Looper.getMainLooper())
    lateinit var serverSocket : ServerSocket
    private lateinit var msgList : LinearLayout
    private lateinit var userInputField : EditText
    lateinit var serverThread : Thread
    lateinit var tempClientSocket : Socket
    private var started = false
    private var messageLog : StringBuilder = StringBuilder()

    private var secretKey: SecretKeySpec? = null
    private lateinit var key: ByteArray
    private val secret = "chiliwestie"
    private val iv = "eBAzNTNhLkQ1Njc4UUFaEK=="

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle("Ryhmä-keskustelu")
        msgList = findViewById(R.id.msgList)
        userInputField = findViewById(R.id.user_input)
    }

    private fun textView(message: String) : TextView{
        var message_input = message
        if(null == message || message.trim().isEmpty()) {  }
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

    fun showMessage(message: String){
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
        if(view.id == R.id.sendButton) {
            if(!started){
                serverThread = Thread(ServerThread())
                serverThread.start()
                val server_button = findViewById<Button>(R.id.sendButton)
                server_button.setText("Send")
                showMessage("Server started...")
                started = true
                loadMessages()
                return
            }
            else {
                val message = userInputField.text.toString().trim()
                showMessage("Tapio: " + message)
                val encrypted_message = encryptMessage("Tapio: " + message)
                if(encrypted_message != null){
                    sendMessage(encrypted_message)
                    saveMessage(encrypted_message)
                }
                return
            }
        }
        if(view.id == R.id.reset) {
            if(msgList.size > 0){
                msgList.removeAllViews()
                val to_be_removed : SharedPreferences = getSharedPreferences("mypref", 0)
                to_be_removed.edit().clear().commit()
            }
        }
    }

    private fun sendMessage(message : String){
        try {
            if(null != tempClientSocket){
                Thread(Runnable {
                    kotlin.run {
                        lateinit var out : PrintWriter
                        try {
                            out = PrintWriter(BufferedWriter(OutputStreamWriter(tempClientSocket.getOutputStream())), true)
                        } catch(e : IOException){
                            e.printStackTrace()
                        }
                        out.println(message)
                    }
                }).start()
            }
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    inner class ServerThread : Runnable {

        override fun run() {
            lateinit var socket : Socket

            try {
                serverSocket = ServerSocket(SERVER_PORT)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if(null != serverSocket) {
                while(!Thread.currentThread().isInterrupted()){
                    try {
                        socket = serverSocket.accept()
                        val commThread = CommunicationThread(socket)
                        Thread(commThread).start()
                    } catch (e : IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    inner class CommunicationThread (clientSocket : Socket) : Runnable {

        var clientSocket : Socket
        lateinit var input : BufferedReader

        init {
            this.clientSocket = clientSocket
            tempClientSocket = clientSocket
            try {
                this.input = BufferedReader(InputStreamReader(this.clientSocket.getInputStream()))
            } catch (e : IOException) {
                e.printStackTrace()
            }
        }

        override fun run() {
            while(!Thread.currentThread().isInterrupted()){
                try {
                    val read = input.readLine()
                    if(null == read || "Disconnect".contentEquals(read)){
                        Thread.interrupted()
                        break
                    }
                    val decrypted_message = decryptMessage(read)
                    if(decrypted_message != null) {
                        showMessage(decrypted_message)
                    }
                    saveMessage(read)
                } catch (e : IOException){
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(null != serverThread){
            sendMessage("Disconnect")
            serverThread.interrupt()
        }
    }
}
