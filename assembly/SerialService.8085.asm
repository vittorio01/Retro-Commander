;packet structure

;- 0xAA - header - command - checksum - data (may be obmitted) - 0xf0 - 

; command ->    a byte which identifies the action that the slave must execute 
; header  ->    bit 7 -> ACK
;               bit 6 -> COUNT 
;               from bit 5 to bit 0 -> data dimension (max 64 bytes)
; checksum ->   used for checking errors. It's a simple 8bit truncated sum of all bytes of the packet (also header,command,checksum, start and stop bytes) 



start_address   .equ $8000 

serial_data_port        .equ %00100110
serial_command_port     .equ %00100111
serial_port_settings    .equ %01001101

serial_state_input_line_mask    .equ %00000010
serial_state_output_line_mask   .equ %00000001

serial_delay_value              .equ 16                 ;delay constant for byte wait functions 
                                                        ;serial_delay_value=(clk-31)/74 
serial_wait_timeout_value       .equ 10000              ;resend timeout value (millis)

serial_packet_start_packet_byte         .equ $AA 
serial_packet_stop_packet_byte          .equ $f0 
serial_packet_dimension_mask            .equ %00111111
serial_packet_max_dimension             .equ 64

serial_packet_acknowledge_bit_mask      .equ %10000000
serial_packet_count_bit_mask            .equ %01000000

serial_packet_resend_attempts           .equ 5 

serial_packet_buffer            .equ    $0200 
serial_packet_count_state       .equ    serial_packet_buffer+serial_packet_max_dimension

serial_command_open_connection_byte     .equ $01
serial_command_close_connection_byte    .equ $02
serial_command_send_identifier_byte     .equ $03 

serial_command_send_terminal_char_byte          .equ $01 
serial_command_request_terminal_char_byte       .equ $02

serial_command_get_disk_informations_byte       .equ $01 
serial_command_write_disk_sector_byte           .equ $02
serial_command_read_disk_sector_byte            .equ $03 


;contains the count state of the two serial lines 
; bit 8 -> send count 
; bit 7 -> receive count   
serial_packet_count_state_send          .equ %10000000
serial_packet_count_state_receive       .equ %01000000

begin:  .org start_address
        jmp start

start:  lxi sp,$7fff
        call serial_line_initialize
        call serial_open_connection
        jnc end 
        call serial_send_boardId
        jnc end 
        call serial_close_connection
        jnc end 
        mvi a,$c0 
        sim 
end:    hlt 


;serial_open_connection sends an open request to the slave 
;Cy -> 1 operation ok, 0 error during transmission 
serial_open_connection:         push b
                                mvi b,serial_command_open_connection_byte  
                                mvi c,0
                                call serial_send_packet
                                pop b
                                ret 

;serial_close_connection sends a close request to the slave 
;Cy -> 1 operation ok, 0 error during transmission 
serial_close_connection:        push b
                                mvi b,serial_command_close_connection_byte  
                                mvi c,0
                                call serial_send_packet
                                pop b
                                ret 

;serial_open_connection sends the boardId to the slave 
;Cy -> 1 operation ok, 0 error during transmission 
serial_send_boardId:            push h 
                                push d 
                                push b 
                                mvi b,serial_command_send_identifier_byte
                                mvi c,0
                                lxi h,serial_packet_buffer
                                lxi d,device_boardId
serial_send_boardId_copy:       ldax d  
                                ora a 
                                jz serial_send_boardId_copy_end
                                mov m,a 
                                inx d 
                                inx h 
                                jmp serial_send_boardId_copy
serial_send_boardId_copy_end:   lxi d,serial_packet_buffer 
                                mov a,l 
                                sub e 
                                ani serial_packet_dimension_mask
                                mov c,a 
                                xchg 
                                call serial_send_packet
                                pop h 
                                pop d 
                                pop b 
                                ret 

;serial_send_terminal_char sends a terminal char to the slave 
;A -> char to send 

serial_send_terminal_char:      push h 
                                push b 
                                lxi h,serial_packet_buffer
                                mov m,a 
                                mvi b,serial_command_send_terminal_char_byte
                                mvi c,1
                                call serial_send_packet
                                pop b 
                                pop h 
                                ret 

;serial_request_terminal_char requests a char from the slave terminal
;A <- char received 

serial_request_terminal_char:           push h 
                                        push b 
serial_request_terminal_char_loop:      lxi h,serial_packet_buffer
                                        mvi b,serial_command_request_terminal_char_byte
                                        mvi c,0
                                        call serial_send_packet 
                                        lxi h,serial_packet_buffer 
                                        call serial_get_packet
                                        mov a,m 
                                        pop b 
                                        pop h 
                                        ret 

;serial_line_initialize resets all serial packet support system 

serial_line_initialize: call serial_configure
                        xra a 
                        sta serial_packet_count_state 
                        ret 

;serial_get_packet read a packet from the serial line, do the checksum and send an ACK to the serial port if it's valid.
;The function will block the normal execution program until it hasn't received a valid packet
;HL -> buffer address

;A <- $ff if the packet is an ACK, $00 otherwise
;C <- data dimension
;B <- command

serial_get_packet:              push d 
                                push h 
                                lxi b,0 
serial_get_packet_wait:         pop h 
                                push h 
                                call serial_wait_new_byte
                                cpi serial_packet_start_packet_byte
                                jnz serial_get_packet_wait
                                call serial_wait_timeout_new_byte
                                jnc serial_get_packet_wait
                                mov e,a                                 ;E <- header 
                                call serial_wait_timeout_new_byte       
                                jnc serial_get_packet_wait      
                                mov b,a                                 ;B <- command
                                call serial_wait_timeout_new_byte 
                                jnc serial_get_packet_wait
                                mov d,a                                 ;D <- checksum
                                mov a,e 
                                ani serial_packet_dimension_mask   
                                jz serial_get_packet_stop_byte           
                                mov c,a                                       
serial_get_packet_bytes_loop:   call serial_wait_timeout_new_byte
                                jnc serial_get_packet_wait
                                mov m,a 
                                inx h 
                                dcr c 
                                jnz serial_get_packet_bytes_loop
serial_get_packet_stop_byte:    call serial_wait_timeout_new_byte
                                jnc serial_get_packet_wait
                                cpi serial_packet_stop_packet_byte
                                jnz serial_get_packet_wait
                                mov a,e 
                                ani serial_packet_dimension_mask 
                                mov c,a 
                                pop h 
                                push h 
                                push b 
                                mvi b,0
                                mov a,c 
                                ora a 
                                jz serial_get_packet_check_end 
serial_get_packet_check_loop:   mov a,m 
                                add b 
                                mov b,a 
                                inx h 
                                dcr c 
                                jnz serial_get_packet_check_loop
serial_get_packet_check_end:    pop psw 
                                mov c,a 
                                add b 
                                add e 
                                adi serial_packet_start_packet_byte
                                adi serial_packet_stop_packet_byte
                                cmp d 
                                jnz serial_get_packet_wait
serial_get_packet_received:     mov b,c
                                mov a,e 
                                ani serial_packet_acknowledge_bit_mask
                                jnz serial_get_packet_count_check
                                mvi a,serial_packet_acknowledge_bit_mask
                                call serial_send_packet 
serial_get_packet_count_check:  lda serial_packet_count_state
                                ani serial_packet_count_state_receive 
                                jz serial_get_packet_count_check2
                                mov a,e 
                                ani serial_packet_count_bit_mask
                                jnz serial_get_packet_wait
                                jmp serial_get_packet_acknowledge
serial_get_packet_count_check2: mov a,e 
                                ani serial_packet_count_bit_mask
                                jz serial_get_packet_wait
serial_get_packet_acknowledge:  mov a,e 
                                ani serial_packet_dimension_mask
                                mov c,a 
                                mov a,e
                                ani serial_packet_acknowledge_bit_mask
                                jz serial_get_packet_end
                                mvi a,$ff 
serial_get_packet_end:          pop h 
                                pop d 
                                ret 



;serial_send_packet sends a packet to the serial line
;A -> $FF if the packet is ACK, $00 otherwise
;C -> packet dimension
;B -> command
;HL -> address to data 
;Cy <- 1 packet transmitted successfully, 0 otherwise


serial_send_packet:             push d 
                                push b 
                                push h 
                                ora a 
                                jz serial_send_packet_init
                                mvi a,serial_packet_acknowledge_bit_mask
                                ora c 
                                mov c,a 
serial_send_packet_init:        mov a,c 
                                ani serial_packet_acknowledge_bit_mask+serial_packet_dimension_mask
                                mov c,a 
                                lda serial_packet_count_state 
                                ani serial_packet_count_state_send
                                jz serial_send_packet2
                                mov a,c 
                                ori serial_packet_count_bit_mask
                                mov c,a 
serial_send_packet2:            mvi e,0
                                mov a,c 
                                ani serial_packet_dimension_mask        ;c -> header
                                mov d,a                                 ;b -> attempts
                                jz serial_send_packet_start_send        ;e -> checksum
                                mvi b,serial_packet_resend_attempts     ;d -> dimension 
serial_send_packet_checksum:    mov a,m 
                                add e 
                                mov e,a 
                                inx h 
                                dcr d 
                                jnz serial_send_packet_checksum
                                mov a,e 
                                add c 
                                add b 
                                adi serial_packet_start_packet_byte
                                adi serial_packet_stop_packet_byte
                                mov e,a 
serial_send_packet_start_send:  mov a,c 
                                ani serial_packet_dimension_mask        
                                mov d,a 
                                pop h 
                                push h 
                                mvi a,serial_packet_start_packet_byte
                                call serial_send_new_byte
                                mov a,c 
                                call serial_send_new_byte 
                                mov a,b
                                call serial_send_new_byte
                                mov a,e 
                                call serial_send_new_byte
                                mov a,d 
                                ora a 
                                jz serial_send_packet_send_stop
serial_send_packet_data:        mov a,m 
                                call serial_send_new_byte
                                inx h 
                                dcr d
                                jnz serial_send_packet_data
serial_send_packet_send_stop:   mvi a,serial_packet_stop_packet_byte
                                call serial_send_new_byte
                                mov a,c 
                                ani serial_packet_acknowledge_bit_mask
                                jnz serial_send_packet_ok
                                call serial_listen_new_byte
                                jc serial_send_packet_ack_check
                                dcr b
                                jnz serial_send_packet_start_send
                                stc 
                                cmc 
                                jmp serial_send_packet_end
serial_send_packet_ack_check:   lxi h,$ffff-serial_packet_max_dimension+1
                                dad sp 
                                call serial_get_packet
                                ora a 
                                jz serial_send_packet_start_send
serial_send_packet_ok:          stc 
serial_send_packet_end:         pop h 
                                pop b 
                                pop d 
                                ret 

;serial_wait_timeout_new_byte does the same function of serial_wait_new_byte can't be read in the timeout 
; Cy <- setted if the function returns a valid value
; A <- byte received if Cy = 1, $00 otherwise

serial_wait_timeout_new_byte:                   push b 
                                                push d 
                                                lxi d,serial_wait_timeout_value
serial_wait_Timeout_new_byte_value_reset:       mvi b,serial_delay_value                        ;7      
serial_wait_timeout_new_byte_value_check:       call serial_get_input_state                     ;17     ---
                                                ora a                                           ;4
                                                jnz serial_wait_timeout_new_byte_received       ;10
                                                dcr b                                           ;5
                                                jnz serial_wait_timeout_new_byte_value_check    ;10     --> 74
                                                dcx d                                           ;5
                                                mov a,e                                         ;5
                                                ora d                                           ;4
                                                jnz serial_wait_Timeout_new_byte_value_reset    ;10
                                                xra a
                                                stc 
                                                cmc 
                                                jmp serial_wait_timeout_new_byte_end
serial_wait_timeout_new_byte_received:          call serial_get_byte
                                                stc 
serial_wait_timeout_new_byte_end:               pop d 
                                                pop b 
                                                ret 


;serial_listen_new_byte wait until a new byte is received on the serial line
;Cy -> 0 if timeout, 1 byte received
serial_listen_new_byte:                 push b 
                                        push d 
                                        lxi d,serial_wait_timeout_value
serial_listen_new_byte_reset:           mvi b,serial_delay_value 
serial_listen_new_byte_check:           call serial_get_input_state
                                        ora a 
                                        jnz serial_listen_new_byte_received 
                                        dcr b 
                                        jnz serial_listen_new_byte_check
                                        dcx d 
                                        mov a,e 
                                        ora d 
                                        jnz serial_listen_new_byte_reset
                                        xra a
                                        stc 
                                        cmc 
                                        jmp serial_listen_new_byte_end
serial_listen_new_byte_received:        stc 
serial_listen_new_byte_end:             pop d 
                                        pop b 
                                        ret 

;serial_wait_new_byte wait until the serial device reads a new byte and returns it's value
; A <- received byte

serial_wait_new_byte:   call serial_get_input_state
                        ora a 
                        jz serial_wait_new_byte
                        call serial_get_byte
                        ret 

;serial_send_new_byte wait until the serial device can transmit a new byte and then sends it
;A -> byte so transmit 

serial_send_new_byte:       push psw 
serial_send_new_byte_wait:  call serial_get_output_state
                            ora a 
                            jz serial_send_new_byte_wait
                            pop psw 
                            call serial_send_byte
                            ret 

;serial_get_input_state returns the state of the serial device input line
;A <- $ff if there is an incoming byte, $00 otherwise 
serial_get_input_state:     in serial_command_port                      ;10
                            ani serial_state_input_line_mask            ;7
                            rz                                          ;11
                            mvi a,$ff 
                            ret 

;serial_get_output_state returns the state of the serial device output line 
;A <- $ff if the serial device can transmit a byte, $00 otherwise
serial_get_output_state:    in serial_command_port
                            ani serial_state_output_line_mask
                            rz 
                            mvi a,$ff 
                            ret 

;serial_send_byte sends a new byte to the serial port 
;A -> byte to send
serial_send_byte:   out serial_data_port
                    ret 

;serial_get_byte get the received byte from the serial device 
;A <- byte received
serial_get_byte:    in serial_data_port
                    ret 

;serial_configure resets the serial device and reconfigure all settings
serial_configure:   xra a 	
                    out serial_command_port		
                    out serial_command_port	
                    out serial_command_port	
                    mvi a,%01000000
                    out serial_command_port	
                    mvi a,serial_port_settings
                    out serial_command_port	
                    mvi a,%00110111
                    out serial_command_port	
                    in serial_data_port	
                    ret 

device_boardId          .text   "PHOENIX 1 FULL"
                        .b 0 