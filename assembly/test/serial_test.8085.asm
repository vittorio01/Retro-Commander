start_address       .equ $8000
stack_address       .equ $7fff
rst55_address       .equ 44


usart_data          .equ %00100110
usart_control       .equ %00100111

usart_set_byte      .equ %01001101      ; 1 bit stop - no party - 8 bit lenght - divide by x1

begin:  .org start_address
        jmp start

start:  di
        lxi sp, stack_address
        call device_setup 
        lxi h,test_string
        call string_out 
loop:   call char_in
        call char_out
        jmp loop

device_setup:   push psw 
                push h
				mvi a,0		
				out usart_control		
				out usart_control
				out usart_control
				mvi a,$40
				out usart_control
				mvi a,usart_set_byte
				out usart_control
				mvi a,$37
				out usart_control
				in usart_data
                pop h
                pop psw 
                ret 

string_out:		push psw		
				push b
string_out_1:	mov a,m			
				cpi 00
				jz string_out_2
				mov c,m
				call char_out
				inx h
				jmp string_out_1
string_out_2:	pop b
				pop psw
				ret

char_out: 	    push psw 
char_out_ver:   in usart_control
                ani %00000001 
                jz char_out_ver 
                pop psw
                out usart_data
    			ret

char_in:		in usart_control
                ani %00000010
                jz char_in
char_in_int:    in usart_data
    			ret

test_string     .text "se leggi questo messaggio significa che il programma funziona correttmente"
                .b $0a,$0d,0