;packet structure

;- 0xAA - header - command - checksum - data (may be obmitted) - 0xf0 - 

; command ->    a byte which identifies the action that the slave must execute 
; header  ->    bit 7 -> ACK
;               bit 6 -> COUNT 
;               bit 5 -> type (fast or slow)
;               from bit 4 to bit 0 -> data dimension (max 32 bytes)
; checksum ->   used for checking errors. It's a simple 8bit truncated sum of all bytes of the packet (also header,command,start and stop bytes) 

debug_mode      .var  false

start_address                           .equ    $8000 


terminal_input_char_queue_dimension             .equ 32
terminal_input_char_queue_fixed_space_address   .equ start_address-terminal_input_char_queue_dimension 
terminal_input_char_queue_start_address         .equ terminal_input_char_queue_fixed_space_address-2
terminal_input_char_queue_end_address           .equ terminal_input_char_queue_start_address-2
terminal_input_char_queue_number                .equ terminal_input_char_queue_end_address-1


serial_packet_state                     .equ    terminal_input_char_queue_number-1
serial_packet_timeout_current_value     .equ    serial_packet_state-2

serial_data_port        .equ %00100110
serial_command_port     .equ %00100111
serial_port_settings    .equ %01001101

serial_error_reset_bit          .equ %00010000
serial_rts_bit                  .equ %00100000
serial_receive_enable_bit       .equ %00000100
serial_transmit_enable_bit      .equ %00000001
serial_dtr_enable_bit           .equ %00000010

serial_state_input_line_mask    .equ %00000010
serial_state_output_line_mask   .equ %00000001

serial_delay_value              .equ 16                 ;delay constant for byte wait functions 
                                                        ;serial_delay_value=(clk-31)/74 
serial_wait_timeout_value_short:       .equ 500         ;resend timeout value (millis)
serial_wait_timeout_value_long:        .equ 5000

serial_packet_start_packet_byte         .equ $AA 
serial_packet_stop_packet_byte          .equ $f0 
serial_packet_dimension_mask            .equ %00011111
serial_packet_max_dimension             .equ 32

serial_packet_acknowledge_bit_mask      .equ %10000000
serial_packet_count_bit_mask            .equ %01000000
serial_packet_type_mask                 .equ %00100000

serial_packet_resend_attempts           .equ 3

serial_command_reset_connection_byte    .equ $21
serial_command_send_identifier_byte     .equ $22

serial_command_send_terminal_char_byte          .equ $01
serial_command_request_terminal_char_byte       .equ $02

serial_command_get_disk_informations_byte       .equ $11 
serial_command_write_disk_sector_byte           .equ $12
serial_command_read_disk_sector_byte            .equ $13 




;contains the count state of the two serial lines 
; bit 8 -> send count 
; bit 7 -> receive count   
serial_packet_line_state          .equ %10000000
serial_packet_connection_reset    .equ %01000000

begin:  .org start_address
        jmp  start

start:                  lxi sp,serial_packet_timeout_current_value-2
                        call serial_line_initialize
                        call serial_reset_connection
loopl:					mvi a,$c0 
                        sim 
                        call serial_request_terminal_char
                        mov b,a 
                        mvi a,$40 
                        sim 
                        mov a,b
						call serial_send_terminal_char
						jmp loopl
                       

device_boardId          .text   "FENIX 1 FULL"
                        
device_boardId_dimension .equ 12
;serial_reset_connection sends an open request to the slave and send the board ID

serial_reset_connection:        push b
                                push d 
                                push h 
serial_reset_connection_retry:  mvi b,serial_command_reset_connection_byte  
                                mvi c,0
                                xra a 
                                stc 
                                call serial_send_packet
                                jnc serial_reset_connection_retry
                                lda serial_packet_state 
                                ori serial_packet_connection_reset 
                                sta serial_packet_state
serial_send_boardId:            mvi b,serial_command_send_identifier_byte
                                lxi h,device_boardId
                                mvi c,device_boardId_dimension 
                                xra a 
                                stc
                                call serial_send_packet
                                jnc serial_send_boardId
serial_reset_connection_end:    pop h 
                                pop d 
                                pop b
                                ret 

;serial_send_terminal_char sends a terminal char to the slave 
;A -> char to send 

serial_send_terminal_char:              push h 
                                        push b 
                                        lxi h,$ffff-serial_packet_max_dimension+1
                                        dad sp 
                                        mov m,a 
                                        mvi b,serial_command_send_terminal_char_byte
                                        mvi c,1
serial_send_terminal_char_retry:        xra a 
                                        stc 
                                        
                                        call serial_send_packet
serial_send_terminal_char_end:          pop b 
                                        pop h 
                                        ret 

;serial_request_terminal_char requests a char from the slave terminal
;A <- char received 

serial_request_terminal_char:               push h 
                                            push b 
                                            push d
                                            call serial_buffer_remove_byte
                                            jc serial_request_terminal_char_end
serial_request_terminal_char_retry:         mvi c,0 
                                            mvi b,serial_command_request_terminal_char_byte
                                            stc 
                                            cmc 
                                            call serial_send_packet
                                            jnc serial_request_terminal_char_retry 
                                            mvi a,$ff
                                            call serial_set_new_timeout
                                            lxi h,$ffff-serial_packet_max_dimension+1
                                            dad sp 
                                            stc
                                            call serial_get_packet
                                            jnc serial_request_terminal_char_retry
                                            mov a,b 
                                            cpi serial_command_request_terminal_char_byte
                                            jnz serial_request_terminal_char_retry
                                            mov a,c 
                                            ora a 
                                            jz serial_request_terminal_char_retry
                                            mov a,m 
                                            dcr c 
                                            jz serial_request_terminal_char_end
                                            mov b,a 
                                            inx h 
serial_request_terminal_char_store_chars:   mov a,m 
                                            call serial_buffer_add_byte
                                            ora a 
                                            jz serial_request_terminal_char_received
                                            inx h 
                                            dcr c 
                                            jnz serial_request_terminal_char_store_chars
serial_request_terminal_char_received:      mov a,b
serial_request_terminal_char_end:           pop d 
                                            pop b 
                                            pop h 
                                            ret 

;serial_line_initialize resets all serial packet support system 

serial_line_initialize:     push h
                            call serial_buffer_initialize
                            call serial_configure
                            xra a  
                            sta serial_packet_state 
                            call serial_set_new_timeout
                            pop h 
                            ret 

;serial_buffer_initialize creates variables and space necessary for initialize a circular array.

serial_buffer_initialize:       push h 
                                lxi h,terminal_input_char_queue_fixed_space_address
                                shld terminal_input_char_queue_start_address
                                shld terminal_input_char_queue_end_address 
                                xra a 
                                sta terminal_input_char_queue_number
                                pop h 
                                ret 

;serial_buffer_add_byte adds the specified value in the circular array
;A -> data to insert
;A <- $ff if data is stored correctly, $00 if the array is full

serial_buffer_add_byte:         push b 
                                push d 
                                push h 
                                mov b,a 
                                lda terminal_input_char_queue_number
                                cpi terminal_input_char_queue_dimension
                                jnz serial_buffer_add_byte_next
                                xra a 
                                jz serial_buffer_add_byte_end
serial_buffer_add_byte_next:    inr a 
                                sta terminal_input_char_queue_number
                                lhld terminal_input_char_queue_end_address
                                mov m,b 
                                lxi d,terminal_input_char_queue_fixed_space_address+terminal_input_char_queue_dimension
                                inx h 
                                mov a,l  
                                sub e 
                                mov a,h 
                                sbb d 
                                jc serial_buffer_add_byte_store
                                lxi h,terminal_input_char_queue_fixed_space_address
serial_buffer_add_byte_store:   shld terminal_input_char_queue_end_address
                                mvi a,$ff
serial_buffer_add_byte_end:     pop h 
                                pop d 
                                pop b 
                                ret 

;serial_buffer_remove_byte removes a single byte from the array
;A <- byte to remove
;Cy <= 0 if the array is empty, 1 otherwise

serial_buffer_remove_byte:          push h 
                                    push d 
                                    push b 
                                    lda terminal_input_char_queue_number
                                    ora a 
                                    jnz serial_buffer_remove_byte_next
                                    xra a 
                                    stc 
                                    cmc 
                                    jmp serial_buffer_remove_byte_end
serial_buffer_remove_byte_next:     dcr a 
                                    sta terminal_input_char_queue_number
                                    lhld terminal_input_char_queue_start_address
                                    mov b,m 
                                    lxi d,terminal_input_char_queue_fixed_space_address+terminal_input_char_queue_dimension
                                    inx h 
                                    mov a,l  
                                    sub e 
                                    mov a,h 
                                    sbb d 
                                    jc serial_buffer_remove_byte_store
                                    lxi h,terminal_input_char_queue_fixed_space_address
serial_buffer_remove_byte_store:    shld terminal_input_char_queue_start_address
                                    mov a,b 
                                    stc 
serial_buffer_remove_byte_end:      pop b 
                                    pop d 
                                    pop h 
                                    ret 

;serial_get_packet read a packet from the serial line, do the checksum and send an ACK to the serial port if it's valid.

;CY -> 0 if the packet has to be waited, 1 if a preliminar timeout is needed
;HL -> buffer address

;A <- $ff if the packet is an ACK, $00 otherwise
;C <- data dimension
;B <- command

serial_get_packet:              push d 
                                push psw 
                                push h 
                                call serial_set_rts_on
serial_get_packet_retry:        pop h 
                                pop psw 
                                push psw 
                                push h 
                                jnc serial_get_packet_wait
serial_get_packet_wait_timeout: call serial_wait_timeout_new_byte
                                jc serial_get_packet_begin
                                lxi b,0 
                                xra a 
                                stc 
                                cmc 
                                jmp serial_get_packet_end
serial_get_packet_wait:         call serial_wait_new_byte
serial_get_packet_begin:        cpi serial_packet_start_packet_byte
                                jnz serial_get_packet_retry
                                xra a 
                                call serial_set_new_timeout
                                call serial_wait_timeout_new_byte
                                jnc serial_get_packet_retry
                                mov e,a                                 ;E <- header 
                                call serial_wait_timeout_new_byte       
                                jnc serial_get_packet_retry     
                                mov b,a                                 ;B <- command
                                call serial_wait_timeout_new_byte 
                                jnc serial_get_packet_retry
                                mov d,a                                 ;D <- checksum

                                mov a,e 
                                ani serial_packet_dimension_mask   
                                jz serial_get_packet_stop_byte           
                                mov c,a                                       
serial_get_packet_bytes_loop:   call serial_wait_timeout_new_byte
                                jnc serial_get_packet_retry
                                mov m,a 
                                inx h 
                                dcr c 
                                jnz serial_get_packet_bytes_loop
serial_get_packet_stop_byte:    call serial_wait_timeout_new_byte
                                jnc serial_get_packet_retry
                                cpi serial_packet_stop_packet_byte
                                jnz serial_get_packet_retry
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
                                jnz serial_get_packet_retry
serial_get_packet_received:     mov b,c
                                mov a,e 
                                ani serial_packet_acknowledge_bit_mask
                                jnz serial_get_packet_count_check
                                mov a,e 
                                ani serial_packet_type_mask
                                jz serial_get_packet_count_check
                                push b 
                                mvi b,0 
                                mvi c,0 
                                mvi a,$ff 
                                call serial_send_packet
                                pop b  
serial_get_packet_count_check:  lda serial_packet_state
                                ani serial_packet_line_state  
                                jz serial_get_packet_count_check2
                                mov a,e 
                                ani serial_packet_count_bit_mask
                                jz serial_get_packet_retry
                                mov a,e 
                                ani serial_packet_acknowledge_bit_mask
                                jnz serial_get_packet_acknowledge
                                jmp serial_get_packet_count_switch
serial_get_packet_count_check2: mov a,e 
                                ani serial_packet_count_bit_mask
                                jnz serial_get_packet_retry
                                mov a,e 
                                ani serial_packet_acknowledge_bit_mask
                                jnz serial_get_packet_acknowledge
serial_get_packet_count_switch: lda serial_packet_state
                                xri $ff 
                                ani serial_packet_line_state
                                mov d,a 
                                lda serial_packet_state 
                                ani $ff - serial_packet_line_state
                                ora d 
                                sta serial_packet_state 
serial_get_packet_acknowledge:  mov a,e 
                                ani serial_packet_dimension_mask
                                mov c,a 
								mov a,e
                                ani serial_packet_acknowledge_bit_mask
                                stc 
                                jz serial_get_packet_end
                                mvi a,$ff 
serial_get_packet_end:          pop h 
                                inx sp 
                                inx sp 
                                pop d 
                                call serial_set_rts_off
                                ret 



;serial_send_packet sends a packet to the serial line
;A -> $FF if the packet is ACK, $00 otherwise
;C -> packet dimension
;B -> command
;HL -> address to data 
;Cy -> slow packet

;Cy <- 1 packet transmitted successfully, 0 otherwise


serial_send_packet:             push d 
                                push b 
                                push h 
                                push psw
                                mov a,c 
                                ani serial_packet_dimension_mask
                                mov c,a  
                                pop psw 
                                push psw 
                                jnc serial_send_packet_init_skip
                                mov a,c 
                                ori serial_packet_type_mask
                                mov c,a 
serial_send_packet_init_skip:   pop psw 
                                ora a 
                                jz serial_send_packet_init
                                mov a,c 
                                ori serial_packet_acknowledge_bit_mask+serial_packet_type_mask
                                mov c,a 
serial_send_packet_init:        lda serial_packet_state 
                                ani serial_packet_line_state 
                                jz serial_send_packet2
                                mov a,c 
                                ori serial_packet_count_bit_mask
                                mov c,a 
serial_send_packet2:            mvi e,0
                                mvi b,serial_packet_resend_attempts     ;d -> dimension 
                                mov a,c 
                                ani serial_packet_dimension_mask        ;c -> header
                                mov d,a                                 ;b -> attempts
                                jz serial_send_packet_checksum2         ;e -> checksum
serial_send_packet_checksum:    mov a,m 
                                add e 
                                mov e,a 
                                inx h 
                                dcr d 
                                jnz serial_send_packet_checksum
serial_send_packet_checksum2:   mov a,e 
                                add c 
                                inx sp 
                                inx sp 
                                xthl 
                                add h 
                                xthl 
                                dcx sp 
                                dcx sp 
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
                                inx sp 
                                inx sp 
                                xthl 
                                mov a,h 
                                xthl 
                                dcx sp 
                                dcx sp 
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
                                jnz serial_send_packet_end2
                                mov a,c 
                                ani serial_packet_type_mask
                                jz serial_send_packet_ok
                                push b 
                                lxi h,$ffff-serial_packet_max_dimension+1
                                dad sp 
                                stc 
                                call serial_get_packet 
                                pop b 
                                jnc serial_send_packet_send_retry
                                ora a 
                                jnz serial_send_packet_ok
serial_send_packet_send_retry:  dcr b
                                jnz serial_send_packet_start_send
                                stc 
                                cmc 
                                jmp serial_send_packet_end
serial_send_packet_ok:          lda serial_packet_state 
                                ani serial_packet_line_state 
                                jz serial_send_packet_ok2
                                lda serial_packet_state 
                                ani $ff-serial_packet_line_state 
                                sta serial_packet_state 
                                stc 
                                jmp serial_send_packet_end 
serial_send_packet_ok2:         lda serial_packet_state 
                                ori serial_packet_line_state 
                                sta serial_packet_state 
serial_send_packet_end2:        stc 
serial_send_packet_end:         pop h 
                                pop b 
                                pop d 
                                ret 

;serial_set_new_timeout sets a new value of timeout of input bytes
;A -> timeout type ($ff long, $00 short)

serial_set_new_timeout:         push h 
                                ora a 
                                jz serial_set_new_timeout_short
                                lxi h,serial_wait_timeout_value_long
                                shld serial_packet_timeout_current_value
                                pop h 
                                ret 
serial_set_new_timeout_short:   lxi h,serial_wait_timeout_value_short
                                shld serial_packet_timeout_current_value
                                pop h 
                                ret 


;serial_wait_timeout_new_byte does the same function of serial_wait_new_byte can't be read in the timeout 
; Cy <- setted if the function returns a valid value
; A <- byte received if Cy = 1, $00 otherwise

serial_wait_timeout_new_byte:                   push b 
                                                push h
                                                lhld serial_packet_timeout_current_value
serial_wait_Timeout_new_byte_value_reset:       mvi b,serial_delay_value                        ;7      
serial_wait_timeout_new_byte_value_check:       call serial_get_input_state                     ;17     ---
                                                ora a                                           ;4
                                                jnz serial_wait_timeout_new_byte_received       ;10
                                                dcr b                                           ;5
                                                jnz serial_wait_timeout_new_byte_value_check    ;10     --> 74
                                                dcx h                                           ;5
                                                mov a,l                                         ;5
                                                ora h                                           ;4
                                                jnz serial_wait_Timeout_new_byte_value_reset    ;10
                                                xra a
                                                stc 
                                                cmc 
                                                jmp serial_wait_timeout_new_byte_end
serial_wait_timeout_new_byte_received:          call serial_get_byte
                                                stc 
serial_wait_timeout_new_byte_end:               pop h
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

;serial_set_rts_on enable the RTS line
.if (debug_mode==false)
serial_set_rts_on:      push psw 
                        mvi a,serial_rts_bit+serial_error_reset_bit+serial_transmit_enable_bit+serial_receive_enable_bit+serial_dtr_enable_bit
                        out serial_command_port	
                        pop psw 
                        ret 
.endif 

.if (debug_mode==true) 
serial_set_rts_on:      ret 
.endif 

;serial_set_rts_off disables the RTS line
.if (debug_mode==false)
serial_set_rts_off:     push psw
                        mvi a,serial_error_reset_bit+serial_transmit_enable_bit+serial_receive_enable_bit+serial_dtr_enable_bit
                        out serial_command_port	
                        pop psw 
                        ret 
.endif 

.if (debug_mode==true)
serial_set_rts_off:     ret
.endif 


;serial_get_input_state returns the state of the serial device input line
;A <- $ff if there is an incoming byte, $00 otherwise 
.if (debug_mode==false)
serial_get_input_state:     in serial_command_port                      ;10
                            ani serial_state_input_line_mask            ;7
                            rz                                          ;11
                            mvi a,$ff 
                            ret 
.endif 
.if (debug_mode==true)
serial_get_input_state:     mvi a,$ff
                            ret 
.endif 
;serial_get_output_state returns the state of the serial device output line 
;A <- $ff if the serial device can transmit a byte, $00 otherwise
.if (debug_mode==false)
serial_get_output_state:    in serial_command_port
                            ani serial_state_output_line_mask
                            rz 
                            mvi a,$ff 
                            ret 
.endif 
.if (debug_mode==true)
serial_get_output_state:        mvi a,$ff 
                                ret 
.endif 
;serial_send_byte sends a new byte to the serial port 
;A -> byte to send
serial_send_byte:   out serial_data_port
                    ret 

;serial_get_byte get the received byte from the serial device 
;A <- byte received
serial_get_byte:    in serial_data_port
                    ret 

;serial_configure resets the serial device and reconfigure all settings
.if (debug_mode==false)
serial_configure:   xra a 	
                    out serial_command_port		
                    out serial_command_port	
                    out serial_command_port	
                    mvi a,%01000000
                    out serial_command_port	
                    mvi a,serial_port_settings
                    out serial_command_port	
                    mvi a,serial_error_reset_bit+serial_transmit_enable_bit+serial_receive_enable_bit+serial_dtr_enable_bit
                    out serial_command_port	
                    in serial_data_port	
                    ret 
.endif 

.if (debug_mode==true) 
serial_configure:   ret 
.endif 

