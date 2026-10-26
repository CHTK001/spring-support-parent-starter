/* Generate a QR PNG for marking E2E using the workspace qrcode package. */
const QRCode = require('D:/ch/project/vue-support-parent-starter/apps/vue-support-ai-starter/node_modules/qrcode');
const path = require('path');

const content = process.argv[2];
const out = process.argv[3];
QRCode.toFile(out, content, { width: 256, margin: 1, errorCorrectionLevel: 'M' }, (err) => {
  if (err) {
    console.error('QR_GEN_FAIL', err);
    process.exit(1);
  }
  console.log('QR_OK', path.resolve(out));
});