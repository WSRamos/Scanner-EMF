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
import androidx.compose.foundation.shape.RoundedCornerShape
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
                Thread.sleep(Random.nextLong(60, 180))
            }
        }.start()
    }

    fun stop() {
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
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
// MÓDULO 3: PAINEL DE CONTROLE CENTRAL
// ==========================================
class MainActivity : ComponentActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    private val audioEngine = AudioEngine()
    private lateinit var dictionaryEngine: DictionaryEngine
    private lateinit var tts: TextToSpeech

    private lateinit var sensorManager: SensorManager
    private var magSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    // Estados de Monitoramento
    private var currentMagnitude by mutableFloatStateOf(0f)
    private var bgColor by mutableStateOf(Color.Black)
    private var wordHistory = mutableStateListOf<String>()
    private var magHistory = mutableStateListOf<Float>()
    private var spelledText by mutableStateOf("")

    // MATRIZ DE CHAVES
    private var isEmfActive by mutableStateOf(true)
    private var isOuijaActive by mutableStateOf(false)
    private var isAudioActive by mutableStateOf(false)
    private var isVibActive by mutableStateOf(false)

    // Calibração Física
    private val spikeThreshold = 12f
    private val accelThresholdLight = 1.6f
    private val accelThresholdStrong = 4.2f
    private var isCooldown = false
    private var isOuijaCooldown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        tts = TextToSpeech(this, this)
        
        dictionaryEngine = DictionaryEngine(this)
        dictionaryEngine.loadAsync()

        setContent {
            val animatedBgColor by animateColorAsState(targetValue = bgColor)
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = animatedBgColor) {
                    TacticalHUD(
                        mag = currentMagnitude,
                        history = magHistory,
                        words = wordHistory,
                        spelledString = spelledText,
                        dictSize = dictionaryEngine.wordsList.size,
                        isLoaded = dictionaryEngine.isLoaded,
                        isEmfActive = isEmfActive,
                        isOuijaActive = isOuijaActive,
                        isAudioActive = isAudioActive,
                        isVibActive = isVibActive,
                        onEmfToggle = { isEmfActive = it },
                        onOuijaToggle = { isOuijaActive = it },
                        onAudioToggle = { 
                            isAudioActive = it
                            if (it) audioEngine.startWhiteNoise() else audioEngine.stop()
                        },
                        onVibToggle = { isVibActive = it },
                        onClearOuija = { spelledText = "" }
                    )
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !dictionaryEngine.isLoaded) return
        
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val mag = sqrt((event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2]).toDouble()).toFloat()
            currentMagnitude = mag
            magHistory.add(mag)
            if (magHistory.size > 50) magHistory.removeAt(0)
            
            if (kotlin.math.abs(mag - magHistory.average().toFloat()) > spikeThreshold) {
                if (isEmfActive && !isCooldown) processEMFSpike(mag)
                if (isOuijaActive && !isOuijaCooldown) processOuijaSpike(mag)
            }
        } 
        else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && isVibActive && !isCooldown) {
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
        Thread { Thread.sleep(3500); isCooldown = false }.start()
    }

    private fun processOuijaSpike(mag: Float) {
        isOuijaCooldown = true
        // O último caractere agora é um espaço real para formatar frases
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ " 
        val seed = (mag * 10000).toLong()
        val index = (seed % 27).toInt().let { if (it < 0) -it else it }
        val letter = alphabet[index].toString()

        spelledText += letter
        
        // Limite estendido para 300 caracteres (frases inteiras)
        if (spelledText.length > 300) spelledText = spelledText.takeLast(300)

        // Fala "Espaço" se cair no espaço em branco para orientar a audição
        val voz = if (letter == " ") "Espaço" else letter
        tts.speak(voz, TextToSpeech.QUEUE_ADD, null, "")

        Thread { Thread.sleep(1200); isOuijaCooldown = false }.start()
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

    override fun onResume() { 
        super.onResume()
        magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    
    override fun onPause() { super.onPause(); sensorManager.unregisterListener(this) }
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
// INTERFACE GRÁFICA (PAINEL TÁTICO)
// ==========================================
@Composable
fun TacticalHUD(
    mag: Float, history: List<Float>, words: List<String>, spelledString: String, dictSize: Int, isLoaded: Boolean,
    isEmfActive: Boolean, isOuijaActive: Boolean, isAudioActive: Boolean, isVibActive: Boolean,
    onEmfToggle: (Boolean) -> Unit, onOuijaToggle: (Boolean) -> Unit, onAudioToggle: (Boolean) -> Unit, onVibToggle: (Boolean) -> Unit,
    onClearOuija: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        
        // MATRIZ DE CHAVES
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ModuleButton("DICIONÁRIO", isEmfActive, onEmfToggle)
                ModuleButton("OUIJA", isOuijaActive, onOuijaToggle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ModuleButton("SPIRIT BOX", isAudioActive, onAudioToggle)
                ModuleButton("SISMÓGRAFO", isVibActive, onVibToggle)
            }
        }

        Divider(color = Color.DarkGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // LEITURA DO HARDWARE
        Text("SCANNER TÁTICO", color = Color(0xFF39FF14), fontSize = 14.sp, fontWeight = FontWeight.Black)
        
        if (isEmfActive || isOuijaActive) {
            Text("${"%.2f".format(mag)} μT", color = Color(0xFF39FF14), fontSize = 42.sp, fontWeight = FontWeight.Bold)
        } else {
            Text("SENSOR STANDBY", color = Color.Gray, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        
        if (isLoaded) {
            Text("BANCO DE DADOS: $dictSize", color = Color.Gray, fontSize = 10.sp)
        } else {
            Text("CARREGANDO...", color = Color.Yellow, fontSize = 10.sp)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // RADAR
        Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.White.copy(alpha = 0.05f))) {
            if (isEmfActive || isOuijaActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (history.size > 1) {
                        val w = size.width / 50f
                        for (i in 0 until history.size - 1) {
                            drawLine(Color(0xFF39FF14), Offset(i*w, size.height - (history[i]/100f*size.height).coerceIn(0f, size.height)), Offset((i+1)*w, size.height - (history[i+1]/100f*size.height).coerceIn(0f, size.height)), 3f)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // TERMINAL OUIJA (Texto Contínuo)
        if (isOuijaActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF001A1A), RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Cyan, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TERMINAL DE SOLETRAÇÃO", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    
                    // BOTÃO DE LIMPEZA MANUAL
                    Box(
                        modifier = Modifier
                            .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .border(1.dp, Color.Red, RoundedCornerShape(2.dp))
                            .clickable { onClearOuija() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("WIPE", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (spelledString.isEmpty()) "Aguardando anomalia..." else spelledString,
                    color = Color.White, 
                    fontSize = 18.sp, // Tamanho reduzido para leitura de frases
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // LOG DE EVENTOS (Dicionário)
        LazyColumn(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { 
            items(words) { 
                Text(it.uppercase(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 4.dp)) 
            } 
        }
    }
}

@Composable
fun ModuleButton(label: String, isActive: Boolean, onClick: (Boolean) -> Unit) {
    val bgColor = if (isActive) Color(0xFF39FF14).copy(alpha = 0.2f) else Color.Transparent
    val textColor = if (isActive) Color(0xFF39FF14) else Color.Gray
    val borderColor = if (isActive) Color(0xFF39FF14) else Color.DarkGray

    Box(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable { onClick(!isActive) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
