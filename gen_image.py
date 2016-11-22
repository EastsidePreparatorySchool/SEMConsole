import os

bytes_to_log = 32
filename = "read_image/data.txt"

f = open(filename, "r")
byts = os.stat(filename).st_size

i = 0
while (i < bytes_to_log):
    print hex(ord(f.read(1)))
    i += 1