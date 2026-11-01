package com.smartparking.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.smartparking.exception.ApiException;
import com.smartparking.model.Reservation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** QR booking pass (v3): PNG encoding the pipe-delimited pass payload from the contract. */
@Service
public class QrService {

    static final int SIZE_PX = 240;

    private final ReservationService reservationService;

    public QrService(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    static String payload(Reservation r) {
        return "PARKGRID|RES:" + r.getId()
                + "|SLOT:" + r.getSlot().getSlotCode()
                + "|PLATE:" + r.getVehicle().getLicensePlate()
                + "|FROM:" + r.getStartTime()
                + "|TO:" + r.getEndTime();
    }

    @Transactional(readOnly = true)
    public byte[] qrPngForReservation(Long reservationId) {
        Reservation r = reservationService.findOwnedOrAdminOrThrow(reservationId);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload(r), BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate QR code");
        }
    }
}
