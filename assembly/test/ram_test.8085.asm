start_address   .equ    $8000
ram_start       .equ    $0000
ram_end         .equ    $7fff 

begin:      .org start_address 
            jmp start 

start:          lxi h,ram_start 
                lxi b,ram_end-ram_start 
                mvi d,$01
loop:           mov m,a 
                inx h 
                inr d 
                dcx b 
                mov a,c 
                ora b 
                jnz loop 
                lxi h,ram_start 
                lxi b,ram_end-ram_start
                mvi d,$01
loop2:          mov a,m 
                cmp d 
                jnz check_fail 
                inx h 
                inr d 
                dcx b 
                mov a,c 
                ora b 
                jnz loop2
                mvi a,$c0 
                sim 
check_fail:     hlt 