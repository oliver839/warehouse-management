package com.logistics.warehouse_management.service;

import com.logistics.warehouse_management.model.DeliveryNote;
import com.logistics.warehouse_management.model.DeliveryNoteLine;
import com.logistics.warehouse_management.model.PickOrder;
import com.logistics.warehouse_management.model.PickOrderLine;
import com.logistics.warehouse_management.repository.DeliveryNoteRepository;
import com.logistics.warehouse_management.repository.PickOrderLineRepository;
import com.logistics.warehouse_management.repository.PickOrderRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PdfDocumentService {

    private final PickOrderRepository pickOrderRepository;
    private final PickOrderLineRepository pickLineRepository;
    private final DeliveryNoteRepository deliveryNoteRepository;

    public PdfDocumentService(PickOrderRepository pickOrderRepository,
                              PickOrderLineRepository pickLineRepository,
                              DeliveryNoteRepository deliveryNoteRepository) {
        this.pickOrderRepository = pickOrderRepository;
        this.pickLineRepository = pickLineRepository;
        this.deliveryNoteRepository = deliveryNoteRepository;
    }

    @Transactional(readOnly = true)
    public byte[] pickList(Long pickOrderId) {
        PickOrder order = pickOrderRepository.findById(pickOrderId)
                .orElseThrow(() -> notFound("Pickauftrag nicht gefunden"));
        return createPdf("Pickliste " + pickOrderId, stream -> {
            write(stream, "PICKLISTE");
            write(stream, "Auftrag: " + safe(order.getProject().getOrderNumber()));
            write(stream, "Status: " + order.getStatus());
            for (PickOrderLine line : pickLineRepository.findByPickOrderIdOrderByBinLocationCodeAsc(pickOrderId)) {
                write(stream, safe(line.getBinLocation().getCode()) + " | "
                        + safe(line.getInventoryItem().getSku()) + " | "
                        + safe(line.getInventoryItem().getName()) + " | Menge: "
                        + line.getRequiredQuantity());
            }
        });
    }

    @Transactional(readOnly = true)
    public byte[] deliveryNote(Long deliveryNoteId) {
        DeliveryNote note = deliveryNoteRepository.findById(deliveryNoteId)
                .orElseThrow(() -> notFound("Lieferschein nicht gefunden"));
        return createPdf("Lieferschein " + note.getDocumentNumber(), stream -> {
            write(stream, "LIEFERSCHEIN");
            write(stream, "Dokument: " + safe(note.getDocumentNumber()));
            write(stream, "Empfaenger: " + safe(note.getRecipient()));
            write(stream, "Lieferadresse: " + safe(note.getDeliveryAddress()));
            write(stream, "Erstellt: " + note.getCreatedAt());
            for (DeliveryNoteLine line : note.getLines()) {
                write(stream, safe(line.getBinLocation().getCode()) + " | "
                        + safe(line.getInventoryItem().getSku()) + " | "
                        + safe(line.getInventoryItem().getName()) + " | Menge: "
                        + line.getQuantity());
            }
        });
    }

    private byte[] createPdf(String title, PdfWriter writer) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                stream.newLineAtOffset(50, 750);
                writer.write(stream);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("PDF konnte nicht erzeugt werden: " + title, exception);
        }
    }

    private void write(PDPageContentStream stream, String text) throws IOException {
        stream.showText(safe(text));
        stream.newLineAtOffset(0, -18);
    }

    private String safe(String value) {
        return value == null ? "-" : value.replace("\n", " ").replace("\r", " ");
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    @FunctionalInterface
    private interface PdfWriter {
        void write(PDPageContentStream stream) throws IOException;
    }
}