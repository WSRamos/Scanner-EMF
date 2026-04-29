package com.hadiyarajesh.composetemplate

import android.content.Context
import android.hardware.*
import android.media.*
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import kotlin.math.sqrt
import kotlin.random.Random

// ==========================================
// MÓDULO 1: MOTOR DE ÁUDIO (SPIRIT BOX)
// ==========================================
class AudioEngine {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    fun startWhiteNoise() {
        if (isPlaying) return
        isPlaying = true
        val bufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM)
        
        Thread {
            val samples = ShortArray(bufferSize)
            audioTrack?.play()
            while (isPlaying) {
                for (i in samples.indices) {
                    samples[i] = (Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())).toShort()
                }
                audioTrack?.write(samples, 0, samples.size)
                Thread.sleep(Random.nextLong(60, 180)) // Efeito de varredura (Sweep)
            }
        }.start()
    }

    fun stop() {
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
    }
}

// ==========================================
// MÓDULO 2: MOTOR DE DICIONÁRIO (DADOS)
// ==========================================
class DictionaryEngine(private val context: Context) {
    val wordsList = mutableListOf<String>()
    var isLoaded by mutableStateOf(false)

    fun loadAsync() {
        Thread {
            try {
                val inputStream = context.assets.open("words.txt")
                val reader = BufferedReader(InputStreamReader(inputStream))
                val tempDict = mutableListOf<String>()
                var line: String? = reader.readLine()
                
                while (line != null) {
                    if (line.trim().isNotEmpty()) tempDict.add(line.trim())
                    line = reader.readLine()
                }
                reader.close()
                wordsList.addAll(tempDict)
                isLoaded = true
            } catch (e: Exception) {
                wordsList.addAll(listOf("erro", "falha", "leitura", "banco", "dados"))
                isLoaded = true
            }
        }.start()
    }

    fun getWordByMagnitude(mag: Float): String {
        if (wordsList.isEmpty()) return "AGUARDE"
        val seed = (mag * 10000).toLong()
        val index = (seed % wordsList.size).toInt().let { if (it < 0) -it else it }
        return wordsList[index]
    }
}

// ==========================================
// MÓDULO 3: PAINEL DE CONTROLE (UI E COORDENAÇÃO)
// ==========================================
class MainActivity : ComponentActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    // Instâncias dos Módulos Independentes
    private val audioEngine = AudioEngine()
    private lateinit var dictionaryEngine: DictionaryEngine
    private lateinit var tts: TextToSpeech

    // Instâncias de Hardware
    private lateinit var sensorManager: SensorManager
    private var magSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    // Estados da Interface (Monitoramento Visível)
    private var currentMagnitude by mutableFloatStateOf(0f)
    private var bgColor by mutableStateOf(Color.Black)
    private var wordHistory = mutableStateListOf<String>()
    private var magHistory = mutableStateListOf<Float>()

    // Calibração Tática
    private val spikeThreshold = 12f
    private val accelThresholdLight = 1.6f
    private val accelThresholdStrong = 4.2f
    private var isCooldown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializa Hardware
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        tts = TextToSpeech(this, this)
        
        // Inicializa Módulos Internos
        dictionaryEngine = DictionaryEngine(this)
        dictionaryEngine.loadAsync()
        audioEngine.startWhiteNoise()

        setContent {
            val animatedBgColor by animateColorAsState(targetValue = bgColor)
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = animatedBgColor) {
                    TacticalHUD(currentMagnitude, magHistory, wordHistory, dictionaryEngine.wordsList.size, dictionaryEngine.isLoaded)
                }
            }
        }
    }

    // ==========================================
    // LÓGICA DE PROCESSAMENTO DE SENSORES
    // ==========================================
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isCooldown || !dictionaryEngine.isLoaded) return
        
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val mag = sqrt((event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2]).toDouble()).toFloat()
            currentMagnitude = mag
            magHistory.add(mag)
            if (magHistory.size > 50) magHistory.removeAt(0)
            
            if (kotlin.math.abs(mag - magHistory.average().toFloat()) > spikeThreshold) processEMFSpike(mag)
            
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val acc = sqrt(event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2]) - 9.8f
            if (acc > accelThresholdStrong) flashScreen(Color(0xFF0000FF), "SIM DETECTADO")
            else if (acc > accelThresholdLight) flashScreen(Color(0xFFFF0000), "NÃO DETECTADO")
        }
    }

    private fun processEMFSpike(mag: Float) {
        isCooldown = true
        val word = dictionaryEngine.getWordByMagnitude(mag)
        
        wordHistory.add(0, word)
        if (wordHistory.size > 8) wordHistory.removeLast()
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "")
        
        Thread { 
            Thread.sleep(3500) 
            isCooldown = false 
        }.start()
    }

    private fun flashScreen(color: Color, label: String) {
        isCooldown = true
        bgColor = color
        wordHistory.add(0, label)
        if (wordHistory.size > 8) wordHistory.removeLast()
        
        Thread {
            Thread.sleep(1500)
            bgColor = Color.Black
            Thread.sleep(500)
            isCooldown = false
        }.start()
    }

    // ==========================================
    // CONTROLE DE CICLO DE VIDA E MEMÓRIA
    // ==========================================
    override fun onResume() { 
        super.onResume()
        magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    
    override fun onPause() { 
        super.onPause()
        sensorManager.unregisterListener(this) 
    }
    
    override fun onDestroy() {
        audioEngine.stop()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
    
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onInit(s: Int) { if (s == TextToSpeech.SUCCESS) tts.language = Locale("pt", "BR") }
}

// ==========================================
// INTERFACE GRÁFICA (JETPACK COMPOSE)
// ==========================================
@Composable
fun TacticalHUD(mag: Float, history: List<Float>, words: List<String>, dictSize: Int, isLoaded: Boolean) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SCANNER TÁTICO MODULAR", color = Color(0xFF39FF14), fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${"%.2f".format(mag)} μT", color = Color(0xFF39FF14), fontSize = 48.sp, fontWeight = FontWeight.Bold)
        
        if (isLoaded) {
            Text("VOCABULÁRIO: $dictSize PALAVRAS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        } else {
            Text("CARREGANDO DADOS...", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(Color.White.copy(alpha = 0.05f))) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (history.size > 1) {
                    val w = size.width / 50f
                    for (i in 0 until history.size - 1) {
                        drawLine(Color(0xFF39FF14), Offset(i*w, size.height - (history[i]/100f*size.height).coerceIn(0f, size.height)), Offset((i+1)*w, size.height - (history[i+1]/100f*size.height).coerceIn(0f, size.height)), 3f)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("AUDIO SWEEP ENGINE: ONLINE", color = Color(0xFFFF3333), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn { 
            items(words) { 
                Text(it.uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 4.dp)) 
            } 
        }
    }
}
        
