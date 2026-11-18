package com.example.pdf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.CarSpec
import com.example.model.GameCatalog
import com.example.model.HudSnapshot
import com.example.model.LevelSpec
import com.example.model.RunRecord
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfReportData(
    val selectedCar: CarSpec,
    val selectedLevel: LevelSpec,
    val hud: HudSnapshot,
    val bestDistance: Int,
    val totalRuns: Int,
    val highestClearedLevel: Int,
    val recentRuns: List<RunRecord>,
)

object GamePdfConverter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    fun generateMissionPdf(context: Context, data: PdfReportData): File {
        val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val outFile = File(pdfDir, "Night_Vision_Drive_3D_Report.pdf")

        val doc = PdfDocument()
        val nowStr = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date())

        // --- PAGE 1: Mission Telemetry & Scorecard ---
        val page1 = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        drawPageOne(context, page1.canvas, data, nowStr)
        doc.finishPage(page1)

        // --- PAGE 2: Vehicle Garage & Day/Night Sector Briefing ---
        val page2 = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create())
        drawPageTwo(page2.canvas, nowStr)
        doc.finishPage(page2)

        FileOutputStream(outFile).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()
        return outFile
    }

    fun renderPreviewPages(file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val scale = 2
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bmp)
                    }
                }
            }
        }
        return bitmaps
    }

    fun copyPdfToUri(context: Context, sourceFile: File, destUri: Uri) {
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(out)
            }
        }
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Night Vision Drive 3D — Mission Dossier PDF")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Mission PDF"))
    }

    fun printPdf(activity: Activity, file: File) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("Night_Vision_Drive_3D_Report.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(2)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                if (cancellationSignal?.isCanceled == true || destination == null) {
                    callback.onWriteCancelled()
                    return
                }
                runCatching {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                }.onSuccess {
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { err ->
                    callback.onWriteFailed(err.message)
                }
            }
        }
        printManager.print("Night Drive 3D Report", adapter, null)
    }

    private fun drawPageOne(context: Context, canvas: Canvas, data: PdfReportData, nowStr: String) {
        val bgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        // Header Banner
        val headerPaint = Paint().apply { color = Color.parseColor("#07111C") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 148f, headerPaint)

        // Accent Emerald Stripe
        val accentPaint = Paint().apply { color = Color.parseColor("#22C55E") }
        canvas.drawRect(0f, 144f, PAGE_WIDTH.toFloat(), 148f, accentPaint)

        // Hero thumbnail on right side of header
        val heroBmp = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_garage_hero)
        }.getOrNull()
        if (heroBmp != null) {
            val dest = RectF(PAGE_WIDTH - 210f, 18f, PAGE_WIDTH - MARGIN, 126f)
            val src = Rect(0, 0, heroBmp.width, heroBmp.height)
            canvas.drawBitmap(heroBmp, src, dest, Paint(Paint.FILTER_BITMAP_FLAG))
            val border = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = Color.parseColor("#22FF66")
            }
            canvas.drawRect(dest, border)
            heroBmp.recycle()
        }

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22FF66")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("TACTICAL 3D HIGHWAY TELEMETRY · PDF DOSSIER", MARGIN, 38f, badgePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 23f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("NIGHT VISION DRIVE 3D", MARGIN, 68f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText("Generated: $nowStr", MARGIN, 92f, subPaint)
        canvas.drawText("Active Car: ${data.selectedCar.name}  |  Sector: LVL ${data.selectedLevel.id} (${data.selectedLevel.timeLabel})", MARGIN, 112f, subPaint)

        var y = 176f
        y = drawSectionHeading(canvas, "LIVE SCORECARD & TELEMETRY SUMMARY", y)

        // 4 KPI Cards (2x2 grid)
        val cardW = (PAGE_WIDTH - MARGIN * 2 - 14f) / 2f
        val cardH = 62f
        drawKpiCard(canvas, MARGIN, y, cardW, cardH, "BEST DISTANCE RECORD", "${data.bestDistance} m", "#16A34A")
        drawKpiCard(canvas, MARGIN + cardW + 14f, y, cardW, cardH, "CURRENT / LAST DISTANCE", "${data.hud.distanceMeters} m", "#0284C7")
        y += cardH + 12f
        drawKpiCard(canvas, MARGIN, y, cardW, cardH, "TOTAL MISSIONS LOGGED", "${data.totalRuns} runs", "#334155")
        drawKpiCard(canvas, MARGIN + cardW + 14f, y, cardW, cardH, "HIGHEST CLEARED SECTOR", if (data.highestClearedLevel > 0) "Level ${data.highestClearedLevel}" else "None yet", "#D97706")
        y += cardH + 24f

        // Active Vehicle Performance Bars
        y = drawSectionHeading(canvas, "SELECTED VEHICLE DIAGNOSTICS · ${data.selectedCar.name.uppercase()}", y)
        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#CBD5E1")
        }
        val specBox = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 104f)
        canvas.drawRoundRect(specBox, 8f, 8f, cardBg)
        canvas.drawRoundRect(specBox, 8f, 8f, cardStroke)

        drawGaugeRow(canvas, MARGIN + 14f, y + 22f, PAGE_WIDTH - MARGIN * 2 - 28f, "Top Speed (${data.selectedCar.topKmhDisplay} km/h)", data.selectedCar.maxSpeed / 100f, "#0EA5E9")
        drawGaugeRow(canvas, MARGIN + 14f, y + 50f, PAGE_WIDTH - MARGIN * 2 - 28f, "Handling Response (${data.selectedCar.handling}x)", data.selectedCar.handling / 1.4f, "#22C55E")
        drawGaugeRow(canvas, MARGIN + 14f, y + 78f, PAGE_WIDTH - MARGIN * 2 - 28f, "NVG Battery Reserve (${data.hud.batteryPercent}%)", data.hud.batteryPercent / 100f, "#F59E0B")
        y += 126f

        // Recent Run History Table
        y = drawSectionHeading(canvas, "RECENT MISSION LOG", y)
        val tableHeaderBg = Paint().apply { color = Color.parseColor("#0F172A") }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f, tableHeaderBg)
        val thText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("TIME", MARGIN + 10f, y + 15.5f, thText)
        canvas.drawText("VEHICLE", MARGIN + 105f, y + 15.5f, thText)
        canvas.drawText("SECTOR", MARGIN + 215f, y + 15.5f, thText)
        canvas.drawText("DIST", MARGIN + 355f, y + 15.5f, thText)
        canvas.drawText("RESULT", MARGIN + 430f, y + 15.5f, thText)
        y += 24f

        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val rows = data.recentRuns.take(8)
        if (rows.isEmpty()) {
            val emptyBox = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 40f)
            canvas.drawRect(emptyBox, cardBg)
            canvas.drawRect(emptyBox, cardStroke)
            canvas.drawText("No completed runs recorded in this session yet — launch a 3D drive to log telemetry.", MARGIN + 12f, y + 24f, rowPaint)
        } else {
            rows.forEachIndexed { idx, run ->
                val rowBg = Paint().apply {
                    color = if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F1F5F9")
                }
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f, rowBg)
                canvas.drawText(run.timestampLabel, MARGIN + 10f, y + 15.5f, rowPaint)
                canvas.drawText(run.carName, MARGIN + 105f, y + 15.5f, rowPaint)
                canvas.drawText(run.levelName, MARGIN + 215f, y + 15.5f, rowPaint)
                canvas.drawText("${run.distanceMeters} m", MARGIN + 355f, y + 15.5f, rowPaint)
                val statusPaint = Paint(rowPaint).apply {
                    color = Color.parseColor(if (run.cleared) "#16A34A" else "#DC2626")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                canvas.drawText(if (run.cleared) "CLEARED" else "CRASHED", MARGIN + 430f, y + 15.5f, statusPaint)
                y += 24f
            }
        }

        drawFooter(canvas, 1, 2)
    }

    private fun drawPageTwo(canvas: Canvas, nowStr: String) {
        val bgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        val headerPaint = Paint().apply { color = Color.parseColor("#07111C") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 66f, headerPaint)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("NIGHT VISION DRIVE 3D · GARAGE & SECTOR MANUAL", MARGIN, 38f, titlePaint)
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22FF66")
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(nowStr, PAGE_WIDTH - MARGIN - 135f, 38f, metaPaint)

        var y = 92f
        y = drawSectionHeading(canvas, "VEHICLE GARAGE ROSTER (4 INTERCEPTORS)", y)

        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#CBD5E1")
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 9.8f
        }

        GameCatalog.cars.forEach { car ->
            val rect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 48f)
            canvas.drawRoundRect(rect, 7f, 7f, cardBg)
            canvas.drawRoundRect(rect, 7f, 7f, cardStroke)

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() or car.colorInt }
            canvas.drawCircle(MARGIN + 18f, y + 24f, 7f, dotPaint)

            canvas.drawText(car.name, MARGIN + 34f, y + 20f, boldPaint)
            canvas.drawText(car.description, MARGIN + 34f, y + 36f, bodyPaint)
            canvas.drawText(
                "Max ${car.topKmhDisplay} km/h  ·  Handling ${car.handling}x  ·  NVG Drain ${car.battDrain}%/s",
                PAGE_WIDTH - MARGIN - 245f,
                y + 27f,
                bodyPaint,
            )
            y += 56f
        }

        y += 12f
        y = drawSectionHeading(canvas, "HIGHWAY SECTORS (DAY, DUSK, NIGHT & CYCLE)", y)

        GameCatalog.levels.forEach { lvl ->
            val rect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 44f)
            canvas.drawRoundRect(rect, 7f, 7f, cardBg)
            canvas.drawRoundRect(rect, 7f, 7f, cardStroke)

            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(if (lvl.isNight) "#0F172A" else "#0284C7")
            }
            canvas.drawRoundRect(RectF(MARGIN + 10f, y + 12f, MARGIN + 78f, y + 32f), 5f, 5f, pillBg)
            val pillTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            canvas.drawText(lvl.timeLabel, MARGIN + 16f, y + 25f, pillTxt)

            canvas.drawText("LVL ${lvl.id}: ${lvl.name}", MARGIN + 90f, y + 20f, boldPaint)
            val targetStr = lvl.targetMeters?.let { "$it m target" } ?: "Endless Cycle"
            canvas.drawText(
                "$targetStr  ·  Speed ${lvl.speedMult}x  ·  Traffic interval ${lvl.trafficRate}s",
                MARGIN + 90f,
                y + 34f,
                bodyPaint,
            )
            y += 52f
        }

        y += 12f
        y = drawSectionHeading(canvas, "CONTROLS & TACTICAL NIGHT VISION GUIDE", y)
        val guideRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 110f)
        canvas.drawRoundRect(guideRect, 8f, 8f, cardBg)
        canvas.drawRoundRect(guideRect, 8f, 8f, cardStroke)

        val tips = listOf(
            "1. Steering: Hold ◀ / ▶ buttons, drag directly across the 3D road, or enable TILT gyro steering.",
            "2. Speed Control: Hold GAS to accelerate to top speed or BRAKE to slow before tight roadblocks.",
            "3. Night Vision (NVG): Activates phosphor green amplification to reveal unlit vehicles in Night/Fog.",
            "4. Battery Pickup: Drive through glowing green energy cubes on the road to restore +35% NVG battery.",
        )
        var ty = y + 24f
        tips.forEach { tip ->
            canvas.drawText(tip, MARGIN + 14f, ty, bodyPaint)
            ty += 22f
        }

        drawFooter(canvas, 2, 2)
    }

    private fun drawSectionHeading(canvas: Canvas, title: String, y: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(title, MARGIN, y, paint)
        val line = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, y + 6f, PAGE_WIDTH - MARGIN, y + 6f, line)
        return y + 18f
    }

    private fun drawKpiCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        label: String,
        value: String,
        accentHex: String,
    ) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#CBD5E1")
        }
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 8f, 8f, bg)
        canvas.drawRoundRect(rect, 8f, 8f, stroke)

        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(accentHex) }
        canvas.drawRoundRect(RectF(x, y, x + 5f, y + h), 4f, 4f, bar)

        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(label, x + 14f, y + 22f, lblPaint)
        canvas.drawText(value, x + 14f, y + 46f, valPaint)
    }

    private fun drawGaugeRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        totalW: Float,
        label: String,
        fraction: Float,
        colorHex: String,
    ) {
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(label, x, y, lblPaint)

        val barX = x + 215f
        val barW = totalW - 215f
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0") }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colorHex) }
        canvas.drawRoundRect(RectF(barX, y - 8f, barX + barW, y + 1f), 4f, 4f, track)
        canvas.drawRoundRect(
            RectF(barX, y - 8f, barX + barW * fraction.coerceIn(0.05f, 1f), y + 1f),
            4f,
            4f,
            fill,
        )
    }

    private fun drawFooter(canvas: Canvas, page: Int, total: Int) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.drawText("Night Drive 3D · Native Android Telemetry & Guide PDF", MARGIN, PAGE_HEIGHT - 20f, footerPaint)
        canvas.drawText("Page $page of $total", PAGE_WIDTH - MARGIN - 55f, PAGE_HEIGHT - 20f, footerPaint)
    }
}
