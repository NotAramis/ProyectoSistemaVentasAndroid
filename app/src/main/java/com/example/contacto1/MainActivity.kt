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

    private val url = "jdbc:mysql://127.0.0.1:3306/sistema_ventas"
    private val user = "root"
    private val pass = "Bayonetta141102@."

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
        val listaDetallesView = findViewById<ListView>(R.id.listaDetalles)

        val btnAgregarDetalle = findViewById<Button>(R.id.btnAgregarDetalle)
        val btnLimpiar = findViewById<Button>(R.id.btnLimpiar)
        val btnGenerarPedido = findViewById<Button>(R.id.btnGenerarPedido)
        val btnVerHistorial = findViewById<Button>(R.id.btnVerHistorial)

        btnVerHistorial.setOnClickListener {
            val intent = android.content.Intent(this, HistorialActivity::class.java)
            startActivity(intent)
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        lblFechaActual.text = sdf.format(Date())

        // Opciones fijas para método de pago
        val pagos = arrayOf("Efectivo", "Tarjeta", "Transferencia", "Crédito")
        spinnerPago.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, pagos))

        adapterDetalles = ArrayAdapter(this, android.R.layout.simple_list_item_1, listaVisual)
        listaDetallesView.adapter = adapterDetalles

        // Agregar detalle usando el precio dinámico de la BD
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

        btnGenerarPedido.setOnClickListener {
            guardarPedidoEnBD()
        }
    }

    override fun onResume() {
        super.onResume()
        // Carga los datos más recientes de la BD cada vez que la pantalla se muestra
        cargarDatosDesdeBD()
    }

    private fun cargarDatosDesdeBD() {
        executor.execute {
            try {
                Class.forName("com.mysql.jdbc.Driver")
                val conn: Connection = DriverManager.getConnection(url, user, pass)

                // 1. Cargar Clientes
                val stmtClientes = conn.createStatement()
                val rsClientes = stmtClientes.executeQuery("SELECT id_cliente, nombre, apellido FROM clientes ORDER BY nombre ASC")
                val tempClientes = mutableListOf<String>()
                while (rsClientes.next()) {
                    val id = rsClientes.getInt("id_cliente")
                    val nombre = rsClientes.getString("nombre")
                    val apellido = rsClientes.getString("apellido")
                    tempClientes.add("$id - $nombre $apellido")
                }

                // 2. Cargar Vendedores/Empleados
                val stmtEmpleados = conn.createStatement()
                val rsEmpleados = stmtEmpleados.executeQuery("SELECT id_empleado, nombre, apellido FROM empleados ORDER BY nombre ASC")
                val tempVendedores = mutableListOf<String>()
                while (rsEmpleados.next()) {
                    val id = rsEmpleados.getInt("id_empleado")
                    val nombre = rsEmpleados.getString("nombre")
                    val apellido = rsEmpleados.getString("apellido")
                    tempVendedores.add("$id - $nombre $apellido")
                }

                // 3. Cargar Productos y Precios
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

                // Actualizar la interfaz en el hilo principal
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
                    Toast.makeText(this@MainActivity, "¡Pedido de Q$totalPedido guardado!", Toast.LENGTH_LONG).show()
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