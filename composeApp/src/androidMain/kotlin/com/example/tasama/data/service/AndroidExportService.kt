package com.example.tasama.data.service

import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.service.ExportService
import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

class AndroidExportService : ExportService {
    override suspend fun exportToExcel(transactions: List<Transaction>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Transactions")
        
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("ID")
        headerRow.createCell(1).setCellValue("Amount")
        headerRow.createCell(2).setCellValue("Type")
        headerRow.createCell(3).setCellValue("Category")
        headerRow.createCell(4).setCellValue("Note")
        headerRow.createCell(5).setCellValue("Date")

        transactions.forEachIndexed { index, transaction ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(transaction.id)
            row.createCell(1).setCellValue(transaction.amount.toDouble())
            row.createCell(2).setCellValue(transaction.type.name)
            row.createCell(3).setCellValue(transaction.category)
            row.createCell(4).setCellValue(transaction.note)
            row.createCell(5).setCellValue(transaction.createdAt.toString())
        }

        val out = ByteArrayOutputStream()
        workbook.write(out)
        workbook.close()
        return out.toByteArray()
    }

    override suspend fun exportToPdf(transactions: List<Transaction>): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document()
        PdfWriter.getInstance(document, out)
        document.open()
        
        document.add(Paragraph("Transaction Report"))
        document.add(Paragraph(" ")) // Spacer

        val table = PdfPTable(6)
        table.widthPercentage = 100f
        table.addCell("ID")
        table.addCell("Amount")
        table.addCell("Type")
        table.addCell("Category")
        table.addCell("Note")
        table.addCell("Date")

        transactions.forEach { transaction ->
            table.addCell(transaction.id)
            table.addCell(transaction.amount.toString())
            table.addCell(transaction.type.name)
            table.addCell(transaction.category)
            table.addCell(transaction.note)
            table.addCell(transaction.createdAt.toString())
        }

        document.add(table)
        document.close()
        return out.toByteArray()
    }
}
