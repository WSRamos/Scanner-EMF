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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : ComponentActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var sensorManager: SensorManager
    private var magSensor: Sensor? = null
    private lateinit var tts: TextToSpeech

    // Variáveis de Estado para o Compose
    private var currentMagnitude by mutableFloatStateOf(0f)
    private var magnitudeHistory = mutableStateListOf<Float>()
    private var wordHistory = mutableStateListOf<String>()

    // Configuração de Gatilho
    private val baselineEMF = 45f // Média do campo geomagnético da Terra (μT)
    private val spikeThreshold = 15f // Sensibilidade: Variação em μT necessária para o gatilho
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

        // Inicialização de Sensores e TTS
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        tts = TextToSpeech(this, this)

        setContent {
            EmfOvilusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    OvilusScreen(
                        magnitude = currentMagnitude,
                        magHistory = magnitudeHistory,
                        words = wordHistory
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        magSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
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
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Cálculo da Magnitude
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            currentMagnitude = magnitude

            // Atualiza gráfico (mantém apenas os últimos 50 valores)
            magnitudeHistory.add(magnitude)
            if (magnitudeHistory.size > 50) {
                magnitudeHistory.removeAt(0)
            }

            // Lógica de Gatilho
            if (kotlin.math.abs(magnitude - baselineEMF) > spikeThreshold && !isCooldown) {
                triggerWord()
                
