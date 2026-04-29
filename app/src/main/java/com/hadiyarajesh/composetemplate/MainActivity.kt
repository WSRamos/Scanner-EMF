package com.hadiyarajesh.composetemplate

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : ComponentActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var sensorManager: SensorManager
    private var magSensor: Sensor? = null
    private lateinit var tts: TextToSpeech

    private var currentMagnitude by mutableFloatStateOf(0f)
    private var magnitudeHistory = mutableStateListOf<Float>()
    private var wordHistory = mutableStateListOf<String>()

    // Linha de base estática removida. Utilizando threshold dinâmico.
    private val spikeThreshold = 15f 
    private var isCooldown = false

    private val dictionary = arrayOf(
        "frio", "aqui", "ajuda", "nome", "perto", "longe", "espírito", "demônio", "luz", "fogo",
        "sangue", "morte", "dor", "raiva", "tristeza", "criança", "mulher", "homem", "sim", "não",
        "talvez", "corra", "saia", "aviso", "perigo", "mal", "paz", "deus", "cuidado", "olhe",
        "atrás", "frente", "baixo", "cima", "chão", "teto", "janela", "porta", "escondido", "medo",
        "vivo", "morto", "alma", "sombra", "eco", "silêncio", "grito", "fale", "ouça", "espere"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        tts = TextToSpeech(this, this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    OvilusScreen(magnitude = currentMagnitude, magHistory = magnitudeHistory, words = wordHistory)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val magnitude = sqrt((event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]).toDouble()).toFloat()
            currentMagnitude = magnitude

            magnitudeHistory.add(magnitude)
            if (magnitudeHistory.size > 50) magnitudeHistory.removeAt(0)

            // Cálculo da Média Móvel: O app aprende o nível do ambiente em tempo real
            val averageEMF = if (magnitudeHistory.isNotEmpty()) magnitudeHistory.average().toFloat() else magnitude

            // Gatilho só dispara se a anomalia for 15μT maior ou menor que a média ATUAL
            if (kotlin.math.abs(magnitude - averageEMF) > spikeThreshold && !isCooldown) triggerWord()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("pt", "BR")
    }

    private fun triggerWord() {
        isCooldown = true
        val randomWord = dictionary[Random.nextInt(dictionary.size)]
        
        wordHistory.add(0, randomWord)
        if (wordHistory.size > 5) wordHistory.removeLast()

        tts.speak(randomWord, TextToSpeech.QUEUE_FLUSH, null, "")

        Thread {
            Thread.sleep(3000)
            isCooldown = false
        }.start()
    }
}

@Composable
fun OvilusScreen(magnitude: Float, magHistory: List<Float>, words: List<String>) {
    val neonGreen = Color(0xFF39FF14)
    val radarRed = Color(0xFFFF0000)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SCANNER EMF", color = neonGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Text("${"%.2f".format(magnitude)} μT", color = if (magnitude > 60f) radarRed else neonGreen, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.DarkGray.copy(alpha = 0.3f)).padding(8.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (magHistory.size > 1) {
                    val widthPerPoint = size.width / 50f
                    val maxHeight = size.height
                    for (i in 0 until magHistory.size - 1) {
                        drawLine(color = neonGreen, start = Offset(i * widthPerPoint, maxHeight - ((magHistory[i] / 100f) * maxHeight).coerceIn(0f, maxHeight)), end = Offset((i + 1) * widthPerPoint, maxHeight - ((magHistory[i + 1] / 100f) * maxHeight).coerceIn(0f, maxHeight)), strokeWidth = 3f)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("HISTÓRICO", color = neonGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            items(words) { word -> Text(word.uppercase(), color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}
// FIM DO CÓDIGO
