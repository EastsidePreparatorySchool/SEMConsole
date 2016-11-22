from usb.core import find
from usb.backend import openusb
import sys

if len(sys.argv) < 2:
    print ('Usage: python test-openusb.py idvendor:idproduct (in hex)')
    exit(1)

vendor, product = list(map(lambda x: int(x, 16), sys.argv[1].split(':')))

b = openusb.get_backend()

if b is None:
    print ('OpenUSB not found')
    exit(1)

device = find(backend=b, idVendor=vendor, idProduct=product)

if device is None:
    print ('Device not found')
    exit(1)

print (device.product)