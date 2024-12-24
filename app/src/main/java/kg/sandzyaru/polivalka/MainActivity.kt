package kg.sandzyaru.polivalka

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PlantWateringApp(this)
        }
    }
}
@Composable
fun PlantWateringApp(context: Context) {
    val humidity = remember { mutableStateOf(0) }
    val isWatering = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val ipAddressState = remember { mutableStateOf(getDeviceIpAddress(context)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1B3C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = ipAddressState.value,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Humidity: ${humidity.value}%",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            LinearProgressIndicator(
                progress = humidity.value / 100f,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp),
                color = Color.Cyan,
                trackColor = Color.Gray
            )

            Text(
                text = if (isWatering.value) "Watering: ON" else "Watering: OFF",
                color = if (isWatering.value) Color.Green else Color.Red,
                fontSize = 18.sp
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        // Отправка команды включения/выключения мотора
                        if (isWatering.value) {
                            sendWateringCommand(isWatering, "0") // Отключаем мотор
                        } else {
                            sendWateringCommand(isWatering, "1") // Включаем мотор
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6200EA),
                    contentColor = Color.White
                )
            ) {
                Text(text = if (isWatering.value) "Stop Watering" else "Water Plant")
            }

        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            val newHumidity = fetchHumidityFromRaspberryPi()
            // Преобразуем значение: 0 -> 100% влажность, 1023 -> 0% влажность
            humidity.value = ((1 - newHumidity / 1023.0) * 100).toInt().coerceIn(0, 100)
        }
    }
}

fun getDeviceIpAddress(context: Context): String {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        for (networkInterface in interfaces) {
            val addresses = networkInterface.inetAddresses.toList()
            for (address in addresses) {
                if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                    return "IP Address: ${address.hostAddress}"
                }
            }
        }
        "IP Address not found"
    } catch (e: Exception) {
        e.printStackTrace()
        "Error retrieving IP address"
    }
}

suspend fun fetchHumidityFromRaspberryPi(): Int {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("http://192.168.130.154:5000/humidity")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { reader ->
                val response = reader.readText()
                val jsonObject = JSONObject(response)
                jsonObject.getInt("humidity")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}

suspend fun sendWateringCommand(isWatering: MutableState<Boolean>, command: String) {
    withContext(Dispatchers.IO) {
        try {
            val url = URL("http://192.168.130.154:5000/water")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.doOutput = true

            connection.outputStream.use { outputStream ->
                outputStream.write(command.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.d("RESPONSE", "Response: $response")

                isWatering.value = command == "1" // Включаем или выключаем мотор в зависимости от команды
            } else {
                Log.e("ERROR", "Failed to send command. Response code: $responseCode")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


