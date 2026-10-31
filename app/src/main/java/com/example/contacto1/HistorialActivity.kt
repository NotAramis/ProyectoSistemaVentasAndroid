package com.example.contacto1

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors

class HistorialActivity : AppCompatActivity() {

    // Configuración Inalámbrica (API REST por Wi-Fi)
    private val urlApiWifi = "http://10.26.238.174:8080/api/historial"

    // Configuración por Cable USB (JDBC Directo)
    private val urlJdbc = "jdbc:mysql://127.0.0.1:3306/sistema_ventas"
    private val userJdbc = "root"
    private val passJdbc = "Bayonetta141102@."

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var listaHistorialView: ListView
    private val listaHistorial = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        listaHistorialView = findViewById(R.id.listaHistorial)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listaHistorial)
        listaHistorialView.adapter = adapter

        // Recibir la preferencia de conexión enviada desde MainActivity (default: Wi-Fi = true)
        val modoWifi = intent.getBooleanExtra("modoWifi", true)

        if (modoWifi) {
            cargarHistorialDesdeAPI()
        } else {
            cargarHistorialDesdeBD()
        }
    }

    // 1. Carga por Wi-Fi mediante API REST en Spring Boot
    private fun cargarHistorialDesdeAPI() {
        executor.execute {
            try {
                val url = URL(urlApiWifi)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonArray = JSONArray(response.toString())
                val tempHistorial = mutableListOf<String>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    // Extraer ID (soporta tanto 'id' como 'id_orden_venta')
                    val id = if (obj.has("id")) obj.getInt("id") else obj.optInt("id_orden_venta", 0)
                    val total = obj.optDouble("total", 0.00)
                    val estado = if (!obj.isNull("estado")) obj.getString("estado") else "Pendiente"
                    val metodoPago = if (!obj.isNull("metodo_pago")) obj.getString("metodo_pago") else "Sin especificar"

                    tempHistorial.add("Orden #$id - Q$total | $metodoPago ($estado)")
                }

                runOnUiThread {
                    listaHistorial.clear()
                    listaHistorial.addAll(tempHistorial)
                    adapter.notifyDataSetChanged()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@HistorialActivity, "Error al cargar por Wi-Fi: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 2. Carga por Cable USB mediante conexión directa JDBC a MySQL
    private fun cargarHistorialDesdeBD() {
        executor.execute {
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn: Connection = DriverManager.getConnection(urlJdbc, userJdbc, passJdbc)
                val stmt = conn.createStatement()

                // Consulta ordenada por la columna 'id' de la tabla ordenes_venta
                val rs = stmt.executeQuery("SELECT * FROM ordenes_venta ORDER BY id DESC")

                val tempHistorial = mutableListOf<String>()
                while (rs.next()) {
                    val id = rs.getInt("id")
                    val total = rs.getDouble("total")
                    val estado = rs.getString("estado") ?: "Pendiente"
                    val metodoPago = rs.getString("metodo_pago") ?: "Sin especificar"

                    tempHistorial.add("Orden #$id - Q$total | $metodoPago ($estado)")
                }

                conn.close()

                runOnUiThread {
                    listaHistorial.clear()
                    listaHistorial.addAll(tempHistorial)
                    adapter.notifyDataSetChanged()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@HistorialActivity, "Error al cargar por USB: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}