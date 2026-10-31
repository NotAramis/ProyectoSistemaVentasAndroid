package com.example.contacto1

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class DetallePedido(val idProducto: Int, val nombre: String, val cantidad: Int, val precio: Double, val subtotal: Double)
data class ProductoInfo(val id: Int, val nombre: String, val precio: Double)

class MainActivity : AppCompatActivity() {

    // Configuración Conexión por Cable USB / Directa JDBC
    private val url = "jdbc:mysql://127.0.0.1:3306/sistema_ventas"
    private val user = "root"
    private val pass = "Bayonetta141102@."

    // Configuración Conexión Inalámbrica Wi-Fi por API REST
    private val urlApiWifi = "http://10.26.238.174:8080/api/"

    private val executor = Executors.newSingleThreadExecutor()
    private val detallesGuardados = mutableListOf<DetallePedido>()
    private val listaVisual = mutableListOf<String>()

    // Listas dinámicas para la UI y mapeo de datos
    private val listaClientes = mutableListOf<String>()
    private val listaVendedores = mutableListOf<String>()
    private val listaProductosPresentacion = mutableListOf<String>()
    private val mapaProductos = mutableMapOf<Int, ProductoInfo>()

    private var totalPedido = 0.00
    private lateinit var adapterDetalles: ArrayAdapter<String>

    private lateinit var spinnerClientes: AutoCompleteTextView
    private lateinit var spinnerVendedores: AutoCompleteTextView
    private lateinit var spinnerProductos: AutoCompleteTextView
    private lateinit var spinnerPago: AutoCompleteTextView
    private lateinit var lblTotal: TextView
    private lateinit var txtCantidad: EditText
    private lateinit var txtObservaciones: EditText
    private lateinit var switchModoConexion: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val lblFechaActual = findViewById<TextView>(R.id.lblFechaActual)
        spinnerClientes = findViewById(R.id.spinnerClientes)
        spinnerVendedores = findViewById(R.id.spinnerVendedores)
        spinnerPago = findViewById(R.id.spinnerPago)
        txtObservaciones = findViewById(R.id.txtObservaciones)
        spinnerProductos = findViewById(R.id.spinnerProductos)
        txtCantidad = findViewById(R.id.txtCantidad)
        lblTotal = findViewById(R.id.lblTotal)
        switchModoConexion = findViewById(R.id.switchModoConexion)
        val listaDetallesView = findViewById<ListView>(R.id.listaDetalles)

        val btnAgregarDetalle = findViewById<Button>(R.id.btnAgregarDetalle)
        val btnLimpiar = findViewById<Button>(R.id.btnLimpiar)
        val btnGenerarPedido = findViewById<Button>(R.id.btnGenerarPedido)
        val btnVerHistorial = findViewById<Button>(R.id.btnVerHistorial)

        btnVerHistorial.setOnClickListener {
            val intent = android.content.Intent(this, HistorialActivity::class.java)
            // Pasa el estado del Switch (true = Wi-Fi, false = USB)
            intent.putExtra("modoWifi", switchModoConexion.isChecked)
            startActivity(intent)
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        lblFechaActual.text = sdf.format(Date())

        val pagos = arrayOf("Efectivo", "Tarjeta", "Transferencia", "Crédito")
        spinnerPago.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, pagos))

        adapterDetalles = ArrayAdapter(this, android.R.layout.simple_list_item_1, listaVisual)
        listaDetallesView.adapter = adapterDetalles

        btnAgregarDetalle.setOnClickListener {
            val seleccion = spinnerProductos.text.toString()
            val cantidadStr = txtCantidad.text.toString()

            if (seleccion.isNotEmpty() && cantidadStr.isNotEmpty()) {
                try {
                    val idProducto = seleccion.split(" - ")[0].toInt()
                    val productoObj = mapaProductos[idProducto]

                    if (productoObj != null) {
                        val cantidad = cantidadStr.toInt()
                        val precio = productoObj.precio
                        val subtotal = cantidad * precio

                        detallesGuardados.add(DetallePedido(idProducto, productoObj.nombre, cantidad, precio, subtotal))
                        listaVisual.add("$cantidad x ${productoObj.nombre} = Q$subtotal")
                        adapterDetalles.notifyDataSetChanged()

                        totalPedido += subtotal
                        lblTotal.text = "Total: Q$totalPedido"
                        txtCantidad.text.clear()
                    } else {
                        Toast.makeText(this, "Producto no encontrado", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Formato inválido", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Selecciona producto y cantidad", Toast.LENGTH_SHORT).show()
            }
        }

        btnLimpiar.setOnClickListener {
            limpiarFormulario()
        }

        switchModoConexion.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this, "Modo Wi-Fi (API REST) activado", Toast.LENGTH_SHORT).show()
                cargarDatosDesdeAPI()
            } else {
                Toast.makeText(this, "Modo Cable USB (JDBC) activado", Toast.LENGTH_SHORT).show()
                cargarDatosDesdeBD()
            }
        }

        btnGenerarPedido.setOnClickListener {
            if (switchModoConexion.isChecked) {
                guardarPedidoDesdeAPI()
            } else {
                guardarPedidoEnBD()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::switchModoConexion.isInitialized && switchModoConexion.isChecked) {
            cargarDatosDesdeAPI()
        } else {
            cargarDatosDesdeBD()
        }
    }

    private fun obtenerJsonApi(endpoint: String): String {
        val url = URL(urlApiWifi + endpoint)
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
        return response.toString()
    }

    private fun cargarDatosDesdeAPI() {
        executor.execute {
            try {
                val jsonClientesStr = obtenerJsonApi("clientes")
                val jsonArrayClientes = JSONArray(jsonClientesStr)
                val tempClientes = mutableListOf<String>()
                for (i in 0 until jsonArrayClientes.length()) {
                    val obj = jsonArrayClientes.getJSONObject(i)
                    val id = obj.getInt("id_cliente")
                    val nombre = obj.getString("nombre")
                    val apellido = obj.getString("apellido")
                    tempClientes.add("$id - $nombre $apellido")
                }

                val jsonEmpleadosStr = obtenerJsonApi("empleados")
                val jsonArrayEmpleados = JSONArray(jsonEmpleadosStr)
                val tempVendedores = mutableListOf<String>()
                for (i in 0 until jsonArrayEmpleados.length()) {
                    val obj = jsonArrayEmpleados.getJSONObject(i)
                    val id = obj.getInt("id_empleado")
                    val nombre = obj.getString("nombre")
                    val apellido = obj.getString("apellido")
                    tempVendedores.add("$id - $nombre $apellido")
                }

                val jsonProductosStr = obtenerJsonApi("productos")
                val jsonArrayProductos = JSONArray(jsonProductosStr)
                val tempProductosStr = mutableListOf<String>()
                val tempProductosMap = mutableMapOf<Int, ProductoInfo>()
                for (i in 0 until jsonArrayProductos.length()) {
                    val obj = jsonArrayProductos.getJSONObject(i)
                    val id = obj.getInt("id_producto")
                    val nombre = obj.getString("nombre")
                    val precio = obj.getDouble("precio")

                    tempProductosStr.add("$id - $nombre (Q$precio)")
                    tempProductosMap[id] = ProductoInfo(id, nombre, precio)
                }

                runOnUiThread {
                    listaClientes.clear()
                    listaClientes.addAll(tempClientes)
                    spinnerClientes.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, listaClientes))

                    listaVendedores.clear()
                    listaVendedores.addAll(tempVendedores)
                    spinnerVendedores.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, listaVendedores))

                    listaProductosPresentacion.clear()
                    listaProductosPresentacion.addAll(tempProductosStr)
                    mapaProductos.clear()
                    mapaProductos.putAll(tempProductosMap)
                    spinnerProductos.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, listaProductosPresentacion))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error API Wi-Fi: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun cargarDatosDesdeBD() {
        executor.execute {
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn: Connection = DriverManager.getConnection(url, user, pass)

                val stmtClientes = conn.createStatement()
                val rsClientes = stmtClientes.executeQuery("SELECT id_cliente, nombre, apellido FROM clientes ORDER BY nombre ASC")
                val tempClientes = mutableListOf<String>()
                while (rsClientes.next()) {
                    val id = rsClientes.getInt("id_cliente")
                    val nombre = rsClientes.getString("nombre")
                    val apellido = rsClientes.getString("apellido")
                    tempClientes.add("$id - $nombre $apellido")
                }

                val stmtEmpleados = conn.createStatement()
                val rsEmpleados = stmtEmpleados.executeQuery("SELECT id_empleado, nombre, apellido FROM empleados ORDER BY nombre ASC")
                val tempVendedores = mutableListOf<String>()
                while (rsEmpleados.next()) {
                    val id = rsEmpleados.getInt("id_empleado")
                    val nombre = rsEmpleados.getString("nombre")
                    val apellido = rsEmpleados.getString("apellido")
                    tempVendedores.add("$id - $nombre $apellido")
                }

                val stmtProductos = conn.createStatement()
                val rsProductos = stmtProductos.executeQuery("SELECT id_producto, nombre, precio FROM ventas_productos ORDER BY nombre ASC")
                val tempProductosStr = mutableListOf<String>()
                val tempProductosMap = mutableMapOf<Int, ProductoInfo>()
                while (rsProductos.next()) {
                    val id = rsProductos.getInt("id_producto")
                    val nombre = rsProductos.getString("nombre")
                    val precio = rsProductos.getDouble("precio")

                    tempProductosStr.add("$id - $nombre (Q$precio)")
                    tempProductosMap[id] = ProductoInfo(id, nombre, precio)
                }

                conn.close()

                runOnUiThread {
                    listaClientes.clear()
                    listaClientes.addAll(tempClientes)
                    spinnerClientes.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, listaClientes))

                    listaVendedores.clear()
                    listaVendedores.addAll(tempVendedores)
                    spinnerVendedores.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, listaVendedores))

                    listaProductosPresentacion.clear()
                    listaProductosPresentacion.addAll(tempProductosStr)
                    mapaProductos.clear()
                    mapaProductos.putAll(tempProductosMap)
                    spinnerProductos.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, listaProductosPresentacion))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error al cargar catálogos: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun guardarPedidoDesdeAPI() {
        val clienteSeleccionado = spinnerClientes.text.toString()
        val vendedorSeleccionado = spinnerVendedores.text.toString()
        val pagoSeleccionado = spinnerPago.text.toString()
        val observaciones = txtObservaciones.text.toString()

        if (detallesGuardados.isEmpty() || clienteSeleccionado.isEmpty() || vendedorSeleccionado.isEmpty() || pagoSeleccionado.isEmpty()) {
            Toast.makeText(this, "Faltan datos requeridos (Cliente, Vendedor, Pago o Productos)", Toast.LENGTH_LONG).show()
            return
        }

        val idCliente = clienteSeleccionado.split(" - ")[0].toInt()
        val idVendedor = vendedorSeleccionado.split(" - ")[0].toInt()

        executor.execute {
            try {
                val jsonPedido = JSONObject()
                jsonPedido.put("idCliente", idCliente)
                jsonPedido.put("idEmpleado", idVendedor)
                jsonPedido.put("total", totalPedido)
                jsonPedido.put("metodoPago", pagoSeleccionado)
                jsonPedido.put("observaciones", observaciones)

                val arrayDetalles = JSONArray()
                for (detalle in detallesGuardados) {
                    val objDetalle = JSONObject()
                    objDetalle.put("idProducto", detalle.idProducto)
                    objDetalle.put("cantidad", detalle.cantidad)
                    objDetalle.put("precio", detalle.precio)
                    arrayDetalles.put(objDetalle)
                }
                jsonPedido.put("detalles", arrayDetalles)

                val url = URL(urlApiWifi + "guardarPedido")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val os = conn.outputStream
                val input = jsonPedido.toString().toByteArray(charset("utf-8"))
                os.write(input, 0, input.size)
                os.flush()
                os.close()

                val responseCode = conn.responseCode
                if (responseCode == 200 || responseCode == 201) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "¡Pedido de Q$totalPedido guardado por Wi-Fi!", Toast.LENGTH_LONG).show()
                        limpiarFormulario()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error en servidor (Código $responseCode)", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error al guardar vía Wi-Fi: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun guardarPedidoEnBD() {
        val clienteSeleccionado = spinnerClientes.text.toString()
        val vendedorSeleccionado = spinnerVendedores.text.toString()
        val pagoSeleccionado = spinnerPago.text.toString()
        val observaciones = txtObservaciones.text.toString()

        if (detallesGuardados.isEmpty() || clienteSeleccionado.isEmpty() || vendedorSeleccionado.isEmpty() || pagoSeleccionado.isEmpty()) {
            Toast.makeText(this, "Faltan datos requeridos (Cliente, Vendedor, Pago o Productos)", Toast.LENGTH_LONG).show()
            return
        }

        val idCliente = clienteSeleccionado.split(" - ")[0].toInt()
        val idVendedor = vendedorSeleccionado.split(" - ")[0].toInt()

        executor.execute {
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn: Connection = DriverManager.getConnection(url, user, pass)
                conn.autoCommit = false

                val sqlOrden = "INSERT INTO ordenes_venta (fecha, id_cliente, id_empleado, total, estado, metodo_pago, observaciones) VALUES (CURRENT_DATE(), ?, ?, ?, 'Pendiente', ?, ?)"
                val psOrden = conn.prepareStatement(sqlOrden, Statement.RETURN_GENERATED_KEYS)
                psOrden.setInt(1, idCliente)
                psOrden.setInt(2, idVendedor)
                psOrden.setDouble(3, totalPedido)
                psOrden.setString(4, pagoSeleccionado)
                psOrden.setString(5, observaciones)
                psOrden.executeUpdate()

                val rs = psOrden.generatedKeys
                var idOrdenGenerada = -1
                if (rs.next()) {
                    idOrdenGenerada = rs.getInt(1)
                }

                val sqlDetalle = "INSERT INTO orden_venta_detalles (id_orden_venta, id_producto, cantidad, precio_unitario) VALUES (?, ?, ?, ?)"
                val psDetalle = conn.prepareStatement(sqlDetalle)

                for (detalle in detallesGuardados) {
                    psDetalle.setInt(1, idOrdenGenerada)
                    psDetalle.setInt(2, detalle.idProducto)
                    psDetalle.setInt(3, detalle.cantidad)
                    psDetalle.setDouble(4, detalle.precio)
                    psDetalle.executeUpdate()
                }

                conn.commit()
                conn.close()

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "¡Pedido de Q$totalPedido guardado por USB!", Toast.LENGTH_LONG).show()
                    limpiarFormulario()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error al guardar: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun limpiarFormulario() {
        detallesGuardados.clear()
        listaVisual.clear()
        adapterDetalles.notifyDataSetChanged()
        totalPedido = 0.00
        lblTotal.text = "Total: Q0.00"
        spinnerClientes.text.clear()
        spinnerVendedores.text.clear()
        spinnerPago.text.clear()
        spinnerProductos.text.clear()
        txtObservaciones.text.clear()
    }
}