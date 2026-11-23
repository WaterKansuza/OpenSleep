package org.opensleep.app.core.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.domain.engine.SleepQualityEngine
import org.opensleep.app.domain.model.SleepHealthReport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val qualityEngine: SleepQualityEngine
) {
    suspend fun exportCsvToUri(
        targetUri: Uri,
        report: SleepHealthReport,
        realSessions: List<SleepSessionEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            val byDay = realSessions
                .filter { it.sessionType == SessionType.NIGHT && !it.isIncomplete }
                .groupBy { it.referenceDateEpochDay }

            val sb = StringBuilder()
            sb.append('\uFEFF')
            sb.append("Ngay,Nguon_Do,Che_Do_Thuc,Gio_Thi_Giac,Gio_Day," + "TST_Phut,TIB_Phut,SOL_Phut,WASO_Phut,Hieu_Suat_Pct,Danh_Gia\n")

            var rowCount = 0
            for (day in report.startEpochDay..report.endEpochDay) {
                val dateStr = LocalDate.ofEpochDay(day).toString()
                val main = byDay[day]?.maxByOrNull { it.sleepDurationMs }
            
                if (main == null) {
                    sb.append("${dateStr},Khong_Co_Du_Lieu,,,,,,,,,\n")
                } else {
                    val metrics = qualityEngine.computeMetrics(
                        main,
                        emptyList(),
                        isDemoData = false
                    )
            
                    val offset = ZoneOffset.ofTotalSeconds(main.zoneOffsetSeconds)
            
                    val asleepStr = Instant.ofEpochMilli(main.asleepTimeMs)
                        .atOffset(offset)
                        .toLocalTime()
                        .format(timeFmt)
            
                    val wakeStr = Instant.ofEpochMilli(main.wakeUpTimeMs)
                        .atOffset(offset)
                        .toLocalTime()
                        .format(timeFmt)
            
                    sb.append(
                        listOf(
                            dateStr,
                            main.trackingMode.name,
                            main.wakeMode.name,
                            asleepStr,
                            wakeStr,
                            metrics.tstMinutes,
                            metrics.tibMinutes,
                            metrics.solMinutes ?: "",
                            metrics.wasoMinutes ?: "",
                            metrics.sleepEfficiencyPct ?: "",
                            main.sleepRating ?: ""
                        ).joinToString(",")
                    )
            
                    sb.append('\n')
                    rowCount++
                }
            }

sb.append(
    "\n# OpenSleep Report,${report.rangeLabel}," +
        "Do_Phu ${report.validNightsCount}/${report.totalDays}," +
        "TB ${report.avgSleepMinutes} phut\n"
)

            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                os.write(sb.toString().toByteArray(Charsets.UTF_8))
            } ?: error("Không thể mở tệp CSV để ghi")

            rowCount
        }
    }

    suspend fun exportPdfToUri(
        targetUri: Uri,
        report: SleepHealthReport,
        realSessions: List<SleepSessionEntity>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 13f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 10f
            }

            var y = 48f
            val left = 40f

            canvas.drawText("OpenSleep - Báo cáo sức khỏe giấc ngủ", left, y, titlePaint)
            y += 22f
            canvas.drawText(
                "Khoảng thời gian: ${report.rangeLabel} (${LocalDate.ofEpochDay(report.startEpochDay)} -> ${LocalDate.ofEpochDay(report.endEpochDay)})",
                left, y, bodyPaint
            )
            y += 16f
            canvas.drawText("Độ phủ dữ liệu thật: ${report.validNightsCount}/${report.totalDays} đêm (${report.coveragePct}%)", left, y, bodyPaint)
            y += 26f

            canvas.drawText("1. Tổng quan thời lượng & chất lượng (Ước tính từ cảm biến)", left, y, headerPaint)
            y += 18f
            canvas.drawText("• Ngủ trung bình/đêm: ${report.avgSleepMinutes / 60}h ${report.avgSleepMinutes % 60}p | Trung vị: ${report.medianSleepMinutes / 60}h ${report.medianSleepMinutes % 60}p", left, y, bodyPaint)
            y += 16f
            canvas.drawText("• Hiệu suất ngủ TB: ${report.avgEfficiencyPct?.let { "${it}%" } ?: "--"} | SOL TB: ${report.avgSolMinutes?.let { "${it}p" } ?: "--"} | WASO TB: ${report.avgWasoMinutes?.let { "${it}p" } ?: "--"}", left, y, bodyPaint)
            y += 16f
            canvas.drawText("• Nguồn dữ liệu: ${report.dominantSourceLabel}", left, y, bodyPaint)
            y += 26f

            canvas.drawText("2. Độ đều đặn nhịp ngủ", left, y, headerPaint)
            y += 18f
            val reg = report.regularity
            canvas.drawText("• Giờ ngủ tiêu biểu: ${reg.typicalBedtimeLabel} (±${reg.bedtimeVariabilityMin}p)", left, y, bodyPaint)
            y += 16f
            canvas.drawText("• Giờ dậy tiêu biểu: ${reg.typicalWakeLabel} (±${reg.wakeVariabilityMin}p) | Điểm giữa giấc: ${reg.sleepMidpointLabel}", left, y, bodyPaint)
            y += 16f
            reg.weekendShiftMin?.let { shift ->
                canvas.drawText("• Chênh lệch cuối tuần so với ngày thường: ${shift} phút", left, y, bodyPaint)
                y += 16f
            }
            y += 12f

            canvas.drawText("3. Chi tiết các đêm gần nhất", left, y, headerPaint)
            y += 18f
            val nightRows = realSessions
                .filter { it.sessionType == SessionType.NIGHT && !it.isIncomplete && it.referenceDateEpochDay in report.startEpochDay..report.endEpochDay }
                .sortedByDescending { it.referenceDateEpochDay }
                .take(18)

            nightRows.forEach { s ->
                val dStr = LocalDate.ofEpochDay(s.referenceDateEpochDay).toString()
                val m = qualityEngine.computeMetrics(s, emptyList(), isDemoData = false)
                val line = "${dStr} | ${s.trackingMode.name} | Ngủ ${m.tstMinutes / 60}h${m.tstMinutes % 60}p | SE ${m.sleepEfficiencyPct ?: "--"}%"
                canvas.drawText(line, left, y, bodyPaint)
                y += 15f
            }

            y = 805f
            canvas.drawText("Ghi chú: Báo cáo được tạo cục bộ 100% offline. Các chỉ số giai đoạn là ước tính tham khảo, không thay thế chẩn đoán y khoa.", left, y, mutedPaint)

            document.finishPage(page)
            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                document.writeTo(os)
            } ?: error("Không thể mở tệp PDF để ghi")
            document.close()
        }
    }
}