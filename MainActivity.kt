
package com.example.invoicegenerator

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class InvoiceItem(var description: String = "", var qty: Int = 1, var unitPrice: Double = 0.0)
data class Invoice(
    var sellerName: String = "",
    var sellerAddress: String = "",
    var buyerName: String = "",
    var buyerAddress: String = "",
    var invoiceNumber: String = "",
    var date: String = "",
    var items: List<InvoiceItem> = emptyList()
) {
    fun subtotal() = items.sumOf { it.qty * it.unitPrice }
    fun tax(rate: Double) = subtotal() * rate / 100.0
    fun total(rate: Double) = subtotal() + tax(rate)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    InvoiceApp()
                }
            }
        }
    }
}

@Composable
fun InvoiceApp() {
    val context = LocalContext.current
    var logoUri by remember { mutableStateOf<Uri?>(null) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> logoUri = uri }

    var sellerName by remember { mutableStateOf("My Company Ltd") }
    var sellerAddress by remember { mutableStateOf("123 Business St, City") }
    var buyerName by remember { mutableStateOf("Client Name") }
    var buyerAddress by remember { mutableStateOf("Client Address") }
    var taxPercent by remember { mutableStateOf(5.0) }
    val items = remember { mutableStateListOf(InvoiceItem("Design Work", 1, 150.0)) }

    val invoice = Invoice(
        sellerName = sellerName,
        sellerAddress = sellerAddress,
        buyerName = buyerName,
        buyerAddress = buyerAddress,
        invoiceNumber = "INV-${System.currentTimeMillis()/1000}",
        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
        items = items.toList()
    )
    val format = NumberFormat.getCurrencyInstance()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Invoice Generator", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { pickLogo.launch("image/*") }) { Text("Upload Logo") }
                    Spacer(Modifier.width(12.dp))
                    logoUri?.let { Image(painter = rememberAsyncImagePainter(it), contentDescription = "logo", modifier = Modifier.size(72.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = sellerName, onValueChange = { sellerName = it }, label = { Text("Seller") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sellerAddress, onValueChange = { sellerAddress = it }, label = { Text("Seller Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = buyerName, onValueChange = { buyerName = it }, label = { Text("Buyer") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = buyerAddress, onValueChange = { buyerAddress = it }, label = { Text("Buyer Address") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedTextField(value = taxPercent.toString(), onValueChange = { taxPercent = it.toDoubleOrNull() ?: 0.0 }, label = { Text("Tax %") }, modifier = Modifier.width(140.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Subtotal: ${format.format(invoice.subtotal())}")
                        Text("Tax: ${format.format(invoice.tax(taxPercent))}")
                        Text("Total: ${format.format(invoice.total(taxPercent))}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Items", fontWeight = FontWeight.SemiBold)
        LazyColumn(modifier = Modifier.height(200.dp)) {
            itemsIndexed(items) { idx, item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OutlinedTextField(value = item.description, onValueChange = { item.description = it }, label = { Text("Description") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = item.qty.toString(), onValueChange = { item.qty = it.toIntOrNull() ?: 1 }, label = { Text("Qty") }, modifier = Modifier.width(80.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = item.unitPrice.toString(), onValueChange = { item.unitPrice = it.toDoubleOrNull() ?: 0.0 }, label = { Text("Price") }, modifier = Modifier.width(120.dp))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { items.removeAt(idx) }) { Text("Remove") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { items.add(InvoiceItem()) }) { Text("Add Item") }
            Row {
                Button(onClick = {
                    saveBothFormats(context, invoice.copy(sellerName=sellerName,sellerAddress=sellerAddress,buyerName=buyerName,buyerAddress=buyerAddress,items=items.toList()), taxPercent, logoUri, a4 = false) { ok,msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Export (Mobile)") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    saveBothFormats(context, invoice.copy(sellerName=sellerName,sellerAddress=sellerAddress,buyerName=buyerName,buyerAddress=buyerAddress,items=items.toList()), taxPercent, logoUri, a4 = true) { ok,msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Export (A4)", color = Color.White) }
            }
        }
    }
}

private fun saveBothFormats(context: Context, invoice: Invoice, tax: Double, logoUri: Uri?, a4: Boolean, callback: (Boolean,String)->Unit) {
    // create PDF (A4 or mobile size) and JPEG (large bitmap)
    val pdfSaved = saveInvoicePdf(context, invoice, tax, logoUri, a4)
    val jpegSaved = saveInvoiceJpeg(context, invoice, tax, logoUri, a4)
    if (pdfSaved && jpegSaved) callback(true, "Saved PDF & JPEG in Downloads/Invoices")
    else if (pdfSaved) callback(true, "Saved PDF only")
    else if (jpegSaved) callback(true, "Saved JPEG only")
    else callback(false, "Failed to save files")
}

private fun saveInvoicePdf(context: Context, invoice: Invoice, tax: Double, logoUri: Uri?, a4: Boolean): Boolean {
    return try {
        val width = if (a4) 595 else 800
        val height = if (a4) 842 else 1200
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val logoBitmap = logoUri?.let { context.contentResolver.openInputStream(it)?.use { BitmapFactory.decodeStream(it) } }
        drawInvoiceOnCanvas(canvas, invoice, tax, logoBitmap, width, height)
        document.finishPage(page)
        val filename = "invoice_${if (a4) \"A4\" else \"MOBILE\"}_${System.currentTimeMillis()}.pdf"
        val out = saveToDownloads(context, filename, "application/pdf") ?: return false
        document.writeTo(out)
        out.close()
        document.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun saveInvoiceJpeg(context: Context, invoice: Invoice, tax: Double, logoUri: Uri?, a4:Boolean): Boolean {
    return try {
        val width = if (a4) 2480 else 1200
        val height = if (a4) 3508 else 1800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val logoBitmap = logoUri?.let { context.contentResolver.openInputStream(it)?.use { BitmapFactory.decodeStream(it) } }
        drawInvoiceOnCanvas(canvas, invoice, tax, logoBitmap, width, height)
        val filename = "invoice_${if (a4) \"A4\" else \"MOBILE\"}_${System.currentTimeMillis()}.jpg"
        val out = saveToDownloads(context, filename, "image/jpeg") ?: return false
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.close()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun drawInvoiceOnCanvas(canvas: Canvas, invoice: Invoice, tax: Double, logo: Bitmap?, pageWidth: Int, pageHeight: Int) {
    val paint = Paint()
    paint.isAntiAlias = true
    val margin = 40f * (pageWidth / 595f)
    var y = margin
    val left = margin

    // Header - logo + seller
    logo?.let {
        val scaled = Bitmap.createScaledBitmap(it, (pageWidth * 0.2).toInt(), (pageWidth * 0.2).toInt(), true)
        canvas.drawBitmap(scaled, left, y, paint)
    }
    paint.textSize = 36f * (pageWidth / 595f)
    paint.isFakeBoldText = true
    canvas.drawText(invoice.sellerName, left + (pageWidth * 0.22).toFloat(), y + 30f*(pageWidth/595f), paint)
    paint.textSize = 14f * (pageWidth / 595f)
    paint.isFakeBoldText = false
    y += 80f * (pageWidth / 595f)
    canvas.drawText(invoice.sellerAddress, left, y, paint)
    y += 30f * (pageWidth / 595f)
    canvas.drawText("Invoice #: ${invoice.invoiceNumber}", left, y, paint)
    canvas.drawText("Date: ${invoice.date}", (pageWidth - 200).toFloat(), y, paint)
    y += 30f * (pageWidth / 595f)

    // Table header
    paint.textSize = 16f * (pageWidth / 595f)
    paint.isFakeBoldText = true
    canvas.drawText("Description", left, y, paint)
    canvas.drawText("Qty", (pageWidth * 0.7).toFloat(), y, paint)
    canvas.drawText("Unit", (pageWidth * 0.8).toFloat(), y, paint)
    canvas.drawText("Total", (pageWidth * 0.9).toFloat(), y, paint)
    paint.isFakeBoldText = false
    y += 20f * (pageWidth / 595f)
    canvas.drawLine(left, y, (pageWidth - left), y, paint)
    y += 18f * (pageWidth / 595f)

    // Items
    for (it in invoice.items) {
        canvas.drawText(it.description, left, y, paint)
        canvas.drawText(it.qty.toString(), (pageWidth * 0.7).toFloat(), y, paint)
        canvas.drawText(String.format(Locale.getDefault(), \"%.2f\", it.unitPrice), (pageWidth * 0.8).toFloat(), y, paint)
        canvas.drawText(String.format(Locale.getDefault(), \"%.2f\", it.qty * it.unitPrice), (pageWidth * 0.9).toFloat(), y, paint)
        y += 18f * (pageWidth / 595f)
    }

    y += 20f * (pageWidth / 595f)
    canvas.drawLine((pageWidth * 0.6).toFloat(), y, (pageWidth - left), y, paint)
    y += 20f * (pageWidth / 595f)
    canvas.drawText(\"Subtotal:\", (pageWidth * 0.6).toFloat(), y, paint)
    canvas.drawText(String.format(Locale.getDefault(), \"%.2f\", invoice.subtotal()), (pageWidth * 0.9).toFloat(), y, paint)
    y += 18f * (pageWidth / 595f)
    canvas.drawText(\"Tax (${tax}%):\", (pageWidth * 0.6).toFloat(), y, paint)
    canvas.drawText(String.format(Locale.getDefault(), \"%.2f\", invoice.tax(tax)), (pageWidth * 0.9).toFloat(), y, paint)
    y += 18f * (pageWidth / 595f)
    paint.isFakeBoldText = true
    canvas.drawText(\"Total:\", (pageWidth * 0.6).toFloat(), y, paint)
    canvas.drawText(String.format(Locale.getDefault(), \"%.2f\", invoice.total(tax)), (pageWidth * 0.9).toFloat(), y, paint)
}

private fun saveToDownloads(context: Context, filename: String, mimeType: String): OutputStream? {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Invoices")
        }
        val collection = when {
            mimeType.startsWith("image") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = resolver.insert(collection, contentValues)
        uri?.let { resolver.openOutputStream(it) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
