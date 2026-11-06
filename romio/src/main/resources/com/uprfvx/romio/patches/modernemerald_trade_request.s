@ Modern Emerald 3.6. Assemble Thumb-1 at 0x09F00000.
@ Only the NPC GetTradeSpecies special-table pointer is redirected here.
@ The original routine still rejects eggs and retrieves the party species.
@ Return the requested ID when the displayed species names match; otherwise
@ return the actual ID, preserving the scripts' existing equality check.
.thumb
push {r4, r5, lr}
ldr r3, original
bl call_r3
cmp r0, #0
beq done
movs r4, r0
ldr r1, requested
ldrh r5, [r1]
movs r2, #11
muls r0, r2
movs r1, r5
muls r1, r2
ldr r2, names
adds r0, r0, r2
adds r1, r1, r2
ldr r3, compare
bl call_r3
cmp r0, #0
bne different
movs r0, r5
b done
different:
movs r0, r4
done:
pop {r4, r5, pc}
call_r3:
bx r3
.balign 4
original: .word 0x081F6089
requested: .word 0x020067CA
names: .word 0x086E7518
compare: .word 0x08005C41
