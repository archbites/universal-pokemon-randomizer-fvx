"""Execute the 3.6 trade-request bridge using Unicorn and the supplied ROM.

Usage: python tools/verify_modernemerald_trades.py path/to/modernemerald.gba
Dependencies: keystone-engine==0.9.2, unicorn==2.1.4 (not runtime dependencies).
The original GetTradeSpecies and StringCompare execute from the ROM. Only
GetMonData is stubbed to supply a chosen party species / egg flag.
"""
from pathlib import Path
import re
import struct
import sys

# Optional local development installation, without requiring global packages.
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'build/modernemerald-tools'))
from keystone import Ks, KS_ARCH_ARM, KS_MODE_THUMB
from unicorn import Uc, UC_ARCH_ARM, UC_MODE_THUMB, UC_HOOK_CODE
from unicorn.arm_const import UC_ARM_REG_PC, UC_ARM_REG_LR, UC_ARM_REG_SP, UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R4, UC_ARM_REG_R5, UC_ARM_REG_R6

root = Path(__file__).resolve().parents[1]
rom = Path(sys.argv[1]).read_bytes()
source = root / 'romio/src/main/resources/com/uprfvx/romio/patches/modernemerald_trade_request.s'
code = bytes(Ks(KS_ARCH_ARM, KS_MODE_THUMB).asm(source.read_text(), 0x09F00000)[0])
java = (root / 'romio/src/main/java/com/uprfvx/romio/romhandlers/ModernEmeraldTradeData.java').read_text()
hex_expression = re.search(r'parseHex\((.*?)\);', java, re.S)[1]
assert code == bytes.fromhex(''.join(re.findall(r'"([a-f0-9]+)"', hex_expression)))

uc = Uc(UC_ARCH_ARM, UC_MODE_THUMB)
uc.mem_map(0x08000000, 0x2000000)
uc.mem_map(0x02000000, 0x40000)
uc.mem_map(0x03000000, 0x8000)
uc.mem_write(0x08000000, rom)
uc.mem_write(0x09F00000, code)
chosen, egg = 0, False

def get_mon_data(cpu, address, size, user):
    if address == 0x08192AFC:
        field = cpu.reg_read(UC_ARM_REG_R1)
        assert field in (12, 46), field
        cpu.reg_write(UC_ARM_REG_R0, int(egg) if field == 46 else chosen)
        cpu.reg_write(UC_ARM_REG_PC, cpu.reg_read(UC_ARM_REG_LR))

uc.hook_add(UC_HOOK_CODE, get_mon_data, begin=0x08192AFC, end=0x08192AFC)
uc.mem_write(0x020067C2, b'\0\0')
ids = []
for line in (root / 'romio/src/main/resources/com/uprfvx/romio/romentries/modernemerald_36_species.tsv').read_text().splitlines():
    if line and not line.startswith('#'): ids.append(int(line.split('\t')[0]))
ids += [417, 418, 419, 454, 459, 460, 461]
def name(species):
    start = 0x6E7518 + species * 11
    return rom[start:rom.index(255, start)]
names = {i: name(i) for i in ids}
cases = 0
for requested in ids:
    accepted = [i for i in ids if names[i] == names[requested]]
    rejected = next(i for i in ids if names[i] != names[requested])
    for chosen, egg in [(i, False) for i in accepted + [rejected]] + [(requested, True)]:
        uc.mem_write(0x020067CA, struct.pack('<H', requested))
        uc.reg_write(UC_ARM_REG_SP, 0x03007E00)
        uc.reg_write(UC_ARM_REG_LR, 0x08000001)
        for register in [UC_ARM_REG_R4, UC_ARM_REG_R5, UC_ARM_REG_R6]: uc.reg_write(register, 0x12345678)
        uc.emu_start(0x09F00001, 0x08000000, count=1000)
        assert uc.reg_read(UC_ARM_REG_PC) == 0x08000000, 'Did not return'
        result = uc.reg_read(UC_ARM_REG_R0)
        assert (result == requested) == (not egg and names[chosen] == names[requested]), (requested, chosen, egg, result)
        assert uc.reg_read(UC_ARM_REG_SP) == 0x03007E00
        for register in [UC_ARM_REG_R4, UC_ARM_REG_R5, UC_ARM_REG_R6]: assert uc.reg_read(register) == 0x12345678
        cases += 1
print(f'Passed {cases} ARM execution cases across {len(ids)} species/form IDs; eggs rejected and callee-saved registers preserved.')
print('Shared-name groups:', [[i for i in ids if names[i] == n] for n in dict.fromkeys(names.values()) if sum(names[i] == n for i in ids) > 1])

# Execute the original trade-creation routine with changed normal IVs. The
# perfect-IV branch must still select its independent six bytes of 31s.
sets = {}
created = []
def creation_calls(cpu, address, size, user):
    if address == 0x08192AFC:
        assert cpu.reg_read(UC_ARM_REG_R1) == 57 # player's level
        cpu.reg_write(UC_ARM_REG_R0, 37)
    elif address == 0x0818DD94: # CreateMon: observe native species and level arguments
        created.append((cpu.reg_read(UC_ARM_REG_R1), cpu.reg_read(UC_ARM_REG_R2)))
    elif address == 0x081930F0: # SetMonData: capture the bytes consumed by actual compiled code
        field = cpu.reg_read(UC_ARM_REG_R1)
        if 40 <= field <= 45: sets[field] = cpu.mem_read(cpu.reg_read(UC_ARM_REG_R2), 1)[0]
    elif address not in (0x0818F17C, 0x08143754):
        return
    else:
        cpu.reg_write(UC_ARM_REG_R0, 0) # CalculateMonStats / ItemIsMail
    cpu.reg_write(UC_ARM_REG_PC, cpu.reg_read(UC_ARM_REG_LR))

# New emulator avoids the party-species hook from the preceding tests.
uc = Uc(UC_ARCH_ARM, UC_MODE_THUMB)
for address, size in [(0x08000000, 0x2000000), (0x02000000, 0x40000), (0x03000000, 0x8000)]: uc.mem_map(address, size)
uc.mem_write(0x08000000, rom)
for address in [0x08192AFC, 0x0818DD94, 0x081930F0, 0x0818F17C, 0x08143754]:
    uc.hook_add(UC_HOOK_CODE, creation_calls, begin=address, end=address)
uc.mem_write(0x030036F0, struct.pack('<I', 0x02010000))
for trade in range(4):
    offset = 0x08BC5FF8 + trade * 64
    randomized = bytes((trade * 7 + stat * 5) % 32 for stat in range(6))
    uc.mem_write(offset + 20, randomized)
    for mode in (0, 1):
        sets.clear()
        uc.mem_write(0x02013DA3, bytes([mode << 2]))
        uc.reg_write(UC_ARM_REG_R0, 0)
        uc.reg_write(UC_ARM_REG_R1, trade)
        uc.reg_write(UC_ARM_REG_SP, 0x03007E00)
        uc.reg_write(UC_ARM_REG_LR, 0x08000001)
        uc.emu_start(0x081F5E05, 0x08000000, count=3000)
        assert uc.reg_read(UC_ARM_REG_PC) == 0x08000000
        assert [sets[i] for i in range(40, 46)] == (list(randomized) if mode == 0 else [31]*6), (trade, mode, sets)
        expected_species = struct.unpack_from('<H', rom, offset - 0x08000000 + 12)[0]
        assert created[-1] == (expected_species, 37)
print('Passed 8 native trade-creation cases: normal randomized IVs / perfect-IV override; received species and level preserved.')
