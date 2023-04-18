
start_address   .equ $8000 

serial_data_port        .equ %00100110
serial_command_port     .equ %00100111
serial_port_settings    .equ %01001101

serial_state_input_line_mask    .equ %00000010
serial_state_output_line_mask   .equ %00000001

serial_delay_value              .equ $86
serial_wait_timeout_value       .equ 10000

serial_packet_start_packet_byte         .equ $AA 
serial_packet_stop_packet_byte          .equ $f0 
serial_packet_dimension_mask            .equ $0f 
serial_packet_max_dimension             .equ $0f

serial_packet_acknowledge_bit_mask      .equ %10000000
serial_packet_channel_bit_mask          .equ %01100000
serial_packet_count_bit_mask            .equ %00010000
serial_packet_terminal_channel_value    .equ %01000000
serial_packet_control_channel_value     .equ %00000000
serial_packet_disk_channel_value        .equ %00100000
serial_packet_resend_attempts           .equ 3 

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

start:  .org start_address 
        lxi sp,$7fff
        call serial_line_initialize
        mvi b,15
        lxi h,serial_message 
loop:   mov a,m 
        call serial_send_new_byte
        inx h 
        dcr b 
        jnz loop
        mvi a,$c0 
        sim  
loop2:  call serial_wait_new_byte
        call serial_send_new_byte 
        jmp loop2

        ;call serial_open_connection
        ;call serial_send_boardId
        ;call serial_close_connection
        hlt 

serial_message: .text "TEST8085 SERIAL"

;serial_open_connection sends an open request to the slave 
;Cy -> 1 operation ok, 0 error during transmission 
serial_open_connection:         push h 
                                mvi a,serial_command_open_connection_byte  
                                sta serial_packet_buffer
                                mvi a,serial_packet_control_channel_value+$01 
                                call serial_send_packet
                                pop h 
                                ret 

;serial_close_connection sends a close request to the slave 
;Cy -> 1 operation ok, 0 error during transmission 
serial_close_connection:        push h 
                                mvi a,serial_command_close_connection_byte  
                                sta serial_packet_buffer
                                mvi a,serial_packet_control_channel_value+$01 
                                call serial_send_packet
                                pop h 
                                ret 

;serial_open_connection sends the boardId to the slave 
;Cy -> 1 operation ok, 0 error during transmission 
serial_send_boardId:            push h 
                                push d 
                                mvi a,serial_command_send_identifier_byte
                                sta serial_packet_buffer
                                lxi h,serial_packet_buffer+1 
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
                                inr a 
                                xchg 
                                ori serial_packet_control_channel_value
                                call serial_send_packet
                                pop h 
                                ret 

;serial_send_terminal_char sends a terminal char to the slave 
;A -> char to send 

serial_send_terminal_char:      push h 
                                lxi h,serial_packet_buffer
                                mvi m,serial_command_send_terminal_char_byte
                                sta serial_packet_buffer+1
                                mvi a,serial_packet_terminal_channel_value+2
                                call serial_send_packet
                                pop h 
                                ret 

;serial_request_terminal_char requests a char from the slave terminal
;A <- char received 

serial_request_terminal_char:           push h 
serial_request_terminal_char_loop:      lxi h,serial_packet_buffer
                                        mvi m,serial_command_request_terminal_char_byte
                                        mvi a,1 
                                        sta serial_packet_buffer+1
                                        mvi a,serial_packet_terminal_channel_value+2
                                        call serial_send_packet 
                                        lxi h,serial_packet_buffer 
                                        call serial_get_packet
                                        ani serial_packet_dimension_mask
                                        jz serial_request_terminal_char_loop
                                        mov a,m 
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

;A <- header 

serial_get_packet:              push b 
                                push d 
                                push h 
                                lxi b,0 
serial_get_packet_wait:         call serial_wait_new_byte
                                cpi serial_packet_start_packet_byte
                                jnz serial_get_packet_wait
                                call serial_wait_timeout_new_byte
                                jnc serial_get_packet_wait
                                mov e,a                                 ;E <- header 
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
                                jz serial_get_packet_received
                                mov c,a 
                                pop h 
                                push h 
                                mvi b,0
serial_get_packet_check_loop:   mov a,m 
                                add b 
                                mov b,a 
                                inx h 
                                dcr c 
                                jnz serial_get_packet_check_loop
                                mov a,d 
                                cmp b 
                                jnz serial_get_packet_wait
serial_get_packet_received:     mov a,e 
                                ani serial_packet_acknowledge_bit_mask
                                jnz serial_get_packet_count_check
                                mov a,e 
                                ani serial_packet_channel_bit_mask
                                ori serial_packet_acknowledge_bit_mask
                                call serial_send_packet 
serial_get_packet_count_check:  lda serial_packet_count_state
                                ani serial_packet_count_state_receive 
                                jz serial_get_packet_count_check2
                                mov a,e 
                                ani serial_packet_count_bit_mask
                                jz serial_get_packet_wait
                                jmp serial_get_packet_received
serial_get_packet_count_check2: mov a,e 
                                ani serial_packet_count_bit_mask
                                jnz serial_get_packet_wait
serial_get_packet_acknowledge:  mov a,e 
                                ani serial_packet_acknowledge_bit_mask+serial_packet_channel_bit_mask+serial_packet_dimension_mask
serial_get_packet_end:          pop h 
                                pop d 
                                pop b 
                                ret 


;serial_send_packet sends a packet to the serial line
;A -> packet header
;HL -> address to data 
;Cy <- 1 packet transmitted successfully, 0 otherwise
serial_send_packet:             push d 
                                push b 
                                push h 
                                ani serial_packet_acknowledge_bit_mask+serial_packet_channel_bit_mask+serial_packet_dimension_mask
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
                                pop h 
                                push h 
                                mov a,c 
                                ani serial_packet_dimension_mask        
                                mov d,a 
serial_send_packet_start_send:  mvi a,serial_packet_start_packet_byte
                                call serial_send_new_byte
                                mov a,c 
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
                                dcr d 
                                jnz serial_send_packet_start_send
                                stc 
                                cmc 
                                jmp serial_send_packet_end
serial_send_packet_ack_check:   lxi h,$ffff-serial_packet_max_dimension+1
                                dad sp 
                                call serial_get_packet
                                mov l,a 
                                ani serial_packet_acknowledge_bit_mask
                                jnz serial_send_packet_ok
                                mov a,l 
                                ani serial_packet_channel_bit_mask
                                mov l,a 
                                mov a,c 
                                ani serial_packet_channel_bit_mask
                                cmp l 
                                jz serial_send_packet_ok
                                dcr d 
                                jnz serial_send_packet_start_send
                                stc 
                                cmc 
                                jmp serial_send_packet_end 
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
serial_wait_Timeout_new_byte_value_reset:       mvi b,serial_delay_value 
serial_wait_timeout_new_byte_value_check:       call serial_get_input_state
                                                ora a 
                                                jnz serial_wait_timeout_new_byte_received 
                                                dcr b 
                                                jnz serial_wait_timeout_new_byte_value_check
                                                dcx d 
                                                mov a,e 
                                                ora d 
                                                jnz serial_wait_Timeout_new_byte_value_reset
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
serial_get_input_state:     in serial_command_port
                            ani serial_state_input_line_mask 
                            rz 
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

device_boardId          .text   "PX-MINI 1"
                        .b 0 