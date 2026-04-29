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

class MainActivity : ComponentActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var sensorManager: SensorManager
    private var magSensor: Sensor? = null
    private lateinit var tts: TextToSpeech

    private var currentMagnitude by mutableFloatStateOf(0f)
    private var magnitudeHistory = mutableStateListOf<Float>()
    private var wordHistory = mutableStateListOf<String>()

    private val spikeThreshold = 12f // Sensibilidade aumentada
    private var isCooldown = false

    // DICIONÁRIO EXPANDIDO (200 PALAVRAS)
    private val dictionary = arrayOf(
        "ajuda", "aqui", "agora", "atrás", "amigo", "antigo", "alma", "aviso", "aberto", "baixo",
        "branco", "breve", "bravo", "caos", "casa", "cuidado", "corra", "criança", "céu", "corpo",
        "cima", "calma", "caminho", "chão", "cheio", "caixa", "claro", "dor", "deus", "dentro",
        "depressa", "demônio", "doença", "dormir", "dia", "dizer", "dono", "escuro", "espera", "estranho",
        "espírito", "esconder", "erro", "eles", "ela", "ele", "está", "eu", "falar", "fogo",
        "frio", "fome", "fora", "forte", "frente", "final", "fechado", "fácil", "grito", "gelo",
        "grande", "guardar", "guerra", "homem", "hoje", "hora", "história", "inimigo", "idade", "inferno",
        "irmão", "igreja", "janela", "juntos", "jamais", "jovem", "jogo", "luz", "longe", "livre",
        "livro", "lugar", "lado", "lembrar", "lento", "morte", "medo", "mulher", "mão", "mal",
        "muito", "meu", "mundo", "mudar", "mensagem", "meio", "noite", "nome", "novo", "nunca",
        "nada", "nós", "nuvem", "olhar", "ouvir", "onde", "ontem", "ouro", "ódio", "objeto",
        "perto", "perigo", "paz", "porta", "pobre", "povo", "podre", "preto", "passado", "presente",
        "pai", "pedra", "pequeno", "perda", "perguntar", "poder", "quente", "quem", "quase", "querer",
        "quarto", "queda", "quebrar", "ruim", "raiva", "rio", "roupa", "rápido", "razão", "rezar",
        "sangue", "saia", "sombra", "sim", "sozinho", "sempre", "sentir", "santo", "sinal", "sede",
        "sono", "sujo", "tempo", "triste", "terra", "todo", "tarde", "teto", "traição", "tocar",
        "trabalho", "último", "unido", "urgente", "usar", "velho", "vida", "vazio", "verdade", "vermelho",
        "você", "voltar", "vontade", "vento", "vigiar", "viver", "zero", "zona", "animal", "braço",
        "campo", "doce", "escada", "faca", "garoto", "herança", "ilha", "joelho", "lâmpada", "médico",
        "navio", "osso", "parede", "queijo", "rato", "sapato", "trem", "urso", "velas", "xadrez"
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

            val averageEMF = if (magnitudeHistory.isNotEmpty()) magnitudeHistory.average().toFloat() else magnitude

            // LÓGICA DE MAPEAMENTO DIRETO (TIPO OVILUS)
            if (kotlin.math.abs(magnitude - averageEMF) > spikeThreshold && !isCooldown) {
                // O valor da magnitude decide a palavra, não o sorteio
                triggerOvilusWord(magnitude)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("pt", "BR")
    }

    private fun triggerOvilusWord(magValue: Float) {
        isCooldown = true
        
        // MAPEAMENTO: Usa o valor magnético para indexar o dicionário
        // Ex: 54.7 microteslas vira o índice da palavra
        val index = (magValue.toInt() % dictionary.size).let { if (it < 0) it * -1 else it }
        val selectedWord = dictionary[index]
        
        wordHistory.add(0, selectedWord)
        if (wordHistory.size > 8) wordHistory.removeLast()

        tts.speak(selectedWord, TextToSpeech.QUEUE_FLUSH, null, "")

        Thread {
            Thread.sleep(4000) // Cooldown levemente maior para análise
            isCooldown = false
        }.start()
    }
}

@Composable
fun OvilusScreen(magnitude: Float, magHistory: List<Float>, words: List<String>) {
    val neonGreen = Color(0xFF39FF14)
    val radarRed = Color(0xFFFF0000)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SCANNER EMF PRO", color = neonGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Text("${"%.2f".format(magnitude)} μT", color = if (magnitude > 60f) radarRed else neonGreen, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.DarkGray.copy(alpha = 0.2f)).padding(8.dp)) {
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

        Spacer(modifier = Modifier.height(24.dp))
        Text("DICIONÁRIO ATIVO", color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            items(words) { word -> 
                Text(word.uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(vertical = 6.dp)) 
            }
        }
    }
}
// FIM DO CÓDIGO PRO
