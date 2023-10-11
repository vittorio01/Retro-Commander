begin:  .org $8000
        jmp start 
start:  lxi sp,$7fff 
loop:   out %00100110
        in %00100111
        jmp loop 