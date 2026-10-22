use std::collections::{BTreeMap, HashMap};
use std::fmt::Write as _;
use std::path::Path;

use goblin::Object;
use iced_x86::{Decoder, DecoderOptions, FlowControl, Formatter, Instruction, IntelFormatter};
use md5::{Digest as Md5Digest, Md5};
use serde::Serialize;
use sha2::{Digest as Sha256Digest, Sha256};

const MAX_FILE_BYTES: u64 = 512 * 1024 * 1024;
const MAX_STRINGS: usize = 8000;
const MAX_DISASM_LINES: usize = 8000;
const DISASM_BYTE_BUDGET: usize = 2 * 1024 * 1024;
const MAX_CONTEXT_CHARS: usize = 24_000;

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct SectionInfo {
    pub name: String,
    pub address: u64,
    pub virtual_size: u64,
    pub raw_size: u64,
    pub offset: u64,
    pub entropy: f64,
    pub perms: String,
    pub executable: bool,
    pub writable: bool,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ImportInfo {
    pub library: String,
    pub name: String,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct StringInfo {
    pub offset: u64,
    pub text: String,
    pub kind: String,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct DisasmLine {
    pub address: u64,
    pub bytes: String,
    pub text: String,
    pub target: Option<u64>,
    pub kind: String,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct CallTarget {
    pub address: u64,
    pub calls: u32,
    pub instructions: u32,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct Indicator {
    pub severity: String,
    pub title: String,
    pub detail: String,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct BinaryReport {
    pub path: String,
    pub name: String,
    pub size: u64,
    pub sha256: String,
    pub md5: String,
    pub file_type: String,
    pub arch: String,
    pub bits: u32,
    pub endian: String,
    pub entry: Option<u64>,
    pub image_base: u64,
    pub timestamp: Option<u64>,
    pub entropy: f64,
    pub sections: Vec<SectionInfo>,
    pub imports: Vec<ImportInfo>,
    pub import_count: u32,
    pub exports: Vec<String>,
    pub strings: Vec<StringInfo>,
    pub string_count: u32,
    pub disassembly: Vec<DisasmLine>,
    pub call_targets: Vec<CallTarget>,
    pub indicators: Vec<Indicator>,
    pub warnings: Vec<String>,
}

#[derive(Debug, Default)]
struct Parsed {
    file_type: String,
    arch: String,
    bits: u32,
    endian: String,
    entry: Option<u64>,
    image_base: u64,
    timestamp: Option<u64>,
    sections: Vec<SectionInfo>,
    imports: Vec<ImportInfo>,
    exports: Vec<String>,
    executable: Vec<(u64, u64, u64)>,
}

pub fn analyze_path(path: &Path) -> Result<BinaryReport, String> {
    let meta = std::fs::metadata(path)
        .map_err(|e| format!("Unable to read {}: {e}", path.display()))?;
    if !meta.is_file() {
        return Err("The selected path is not a file.".to_string());
    }
    if meta.len() > MAX_FILE_BYTES {
        return Err(format!(
            "File is {} — the 512 MB limit keeps analysis responsive.",
            format_bytes(meta.len())
        ));
    }

    let data = std::fs::read(path)
        .map_err(|e| format!("Unable to read {}: {e}", path.display()))?;
    let name = path
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("binary")
        .to_string();

    let mut warnings = Vec::new();
    let parsed = parse(&data, &mut warnings);
    let file_entropy = entropy(&data);
    let (strings, string_count) = extract_strings(&data);
    let (disassembly, call_targets) = disassemble(&parsed, &data);
    let indicators = build_indicators(&parsed, file_entropy, meta.len(), &strings);

    if disassembly.is_empty() && !parsed.executable.is_empty() {
        warnings.push(
            "No instructions could be decoded from the executable sections.".to_string(),
        );
    }
    if parsed.executable.is_empty() {
        warnings.push(
            "No executable sections were located, so native disassembly is unavailable."
                .to_string(),
        );
    }
    if parsed.imports.is_empty() {
        warnings.push(
            "No imports were recovered. The binary may be statically linked, packed, or in an unsupported format."
                .to_string(),
        );
    }
    if meta.len() > 64 * 1024 * 1024 {
        warnings.push(
            "Large file: disassembly is capped at 2 MB of executable code and 8000 instructions."
                .to_string(),
        );
    }
    if strings.len() >= MAX_STRINGS {
        warnings.push(format!(
            "String extraction stopped at {MAX_STRINGS} entries."
        ));
    }

    Ok(BinaryReport {
        path: path.display().to_string(),
        name,
        size: meta.len(),
        sha256: hex(&Sha256::digest(&data)),
        md5: hex(&Md5::digest(&data)),
        file_type: parsed.file_type,
        arch: parsed.arch,
        bits: parsed.bits,
        endian: parsed.endian,
        entry: parsed.entry,
        image_base: parsed.image_base,
        timestamp: parsed.timestamp,
        entropy: file_entropy,
        sections: parsed.sections,
        import_count: parsed.imports.len() as u32,
        imports: parsed.imports,
        exports: parsed.exports,
        string_count,
        strings,
        disassembly,
        call_targets,
        indicators,
        warnings,
    })
}

fn parse(data: &[u8], warnings: &mut Vec<String>) -> Parsed {
    match Object::parse(data) {
        Ok(Object::PE(pe)) => parse_pe(&pe, data),
        Ok(Object::ELF(elf)) => parse_elf(&elf, data),
        Ok(Object::Mach(goblin::mach::Mach::Binary(macho))) => parse_macho(&macho, data),
        Ok(Object::Mach(goblin::mach::Mach::Fat(_))) => {
            generic("Mach-O universal (fat) binary", "Unknown", 0)
        }
        Ok(Object::Archive(_)) => generic("Static library archive", "Unknown", 0),
        Ok(Object::Unknown(magic)) => generic_unknown(magic, data),
        Err(err) => {
            warnings.push(format!("Structural parse failed: {err}"));
            generic_unknown(0, data)
        }
    }
}

fn parse_pe(pe: &goblin::pe::PE, data: &[u8]) -> Parsed {
    let bits = if pe.is_64 { 64 } else { 32 };
    let machine = pe.header.coff_header.machine;
    let arch = match machine {
        0x8664 => "x86-64",
        0x014c => "x86",
        0xaa64 => "ARM64",
        0x01c0 => "ARM",
        0x01c4 => "ARM Thumb",
        0x0200 => "IA-64",
        _ => "Unknown",
    }
    .to_string();

    let mut sections = Vec::new();
    let mut executable = Vec::new();
    for section in &pe.sections {
        let name = section
            .name()
            .unwrap_or("")
            .trim_end_matches('\0')
            .to_string();
        let read = section.characteristics & 0x4000_0000 != 0;
        let write = section.characteristics & 0x8000_0000 != 0;
        let exec = section.characteristics & 0x2000_0000 != 0;
        let raw_size = section.size_of_raw_data as u64;
        let offset = section.pointer_to_raw_data as u64;
        let bytes = slice(data, offset, raw_size);
        if exec && raw_size > 0 {
            executable.push((
                section.virtual_address as u64,
                offset,
                raw_size,
            ));
        }
        sections.push(SectionInfo {
            name,
            address: section.virtual_address as u64,
            virtual_size: section.virtual_size as u64,
            raw_size,
            offset,
            entropy: entropy(bytes),
            perms: perms(read, write, exec),
            executable: exec,
            writable: write,
        });
    }

    let mut imports = Vec::new();
    for import in &pe.imports {
        let name = if import.name.is_empty() {
            format!("ordinal_{}", import.ordinal)
        } else {
            import.name.to_string()
        };
        imports.push(ImportInfo {
            library: import.dll.to_string(),
            name,
        });
    }

    let mut exports = Vec::new();
    for export in &pe.exports {
        if let Some(name) = export.name {
            if !name.is_empty() {
                exports.push(name.to_string());
            }
        }
    }

    Parsed {
        file_type: if pe.is_64 {
            "PE32+ executable".to_string()
        } else {
            "PE32 executable".to_string()
        },
        arch,
        bits,
        endian: "little".to_string(),
        entry: Some(pe.entry as u64).filter(|value| *value != 0),
        image_base: pe.image_base,
        timestamp: Some(pe.header.coff_header.time_date_stamp as u64)
            .filter(|value| *value != 0),
        sections,
        imports,
        exports,
        executable,
    }
}

fn parse_elf(elf: &goblin::elf::Elf, data: &[u8]) -> Parsed {
    let bits = if elf.is_64 { 64 } else { 32 };
    let arch = match elf.header.e_machine {
        0x3e => "x86-64",
        0x03 => "x86",
        0x28 => "ARM",
        0xb7 => "ARM64",
        0x08 => "MIPS",
        0x14 => "PowerPC",
        0x16 => "S390",
        0xf3 => "RISC-V",
        _ => "Unknown",
    }
    .to_string();

    let mut sections = Vec::new();
    let mut executable = Vec::new();
    for section in &elf.section_headers {
        let name = elf
            .shdr_strtab
            .get_at(section.sh_name)
            .unwrap_or("")
            .to_string();
        let read = section.sh_flags & 0x2 != 0;
        let write = section.sh_flags & 0x1 != 0;
        let exec = section.sh_flags & 0x4 != 0;
        let bytes = slice(data, section.sh_offset, section.sh_size);
        if exec && section.sh_size > 0 {
            executable.push((section.sh_addr, section.sh_offset, section.sh_size));
        }
        sections.push(SectionInfo {
            name,
            address: section.sh_addr,
            virtual_size: section.sh_size,
            raw_size: section.sh_size,
            offset: section.sh_offset,
            entropy: entropy(bytes),
            perms: perms(read, write, exec),
            executable: exec,
            writable: write,
        });
    }

    if executable.is_empty() {
        for (index, segment) in elf.program_headers.iter().enumerate() {
            if segment.p_type == 1 && segment.p_flags & 1 != 0 && segment.p_filesz > 0 {
                let read = segment.p_flags & 4 != 0;
                let write = segment.p_flags & 2 != 0;
                executable.push((segment.p_vaddr, segment.p_offset, segment.p_filesz));
                sections.push(SectionInfo {
                    name: format!("LOAD[{index}]"),
                    address: segment.p_vaddr,
                    virtual_size: segment.p_memsz,
                    raw_size: segment.p_filesz,
                    offset: segment.p_offset,
                    entropy: entropy(slice(data, segment.p_offset, segment.p_filesz)),
                    perms: perms(read, write, true),
                    executable: true,
                    writable: write,
                });
            }
        }
    }

    let image_base = elf
        .program_headers
        .iter()
        .filter(|segment| segment.p_type == 1)
        .map(|segment| segment.p_vaddr)
        .min()
        .unwrap_or(0);

    let mut imports = Vec::new();
    for symbol in elf.dynsyms.iter() {
        if symbol.st_shndx == 0 {
            if let Some(name) = elf.dynstrtab.get_at(symbol.st_name) {
                if !name.is_empty() {
                    imports.push(ImportInfo {
                        library: "(dynamic)".to_string(),
                        name: name.to_string(),
                    });
                }
            }
        }
    }

    let mut exports = Vec::new();
    for symbol in elf.syms.iter() {
        if symbol.st_shndx != 0 {
            if let Some(name) = elf.strtab.get_at(symbol.st_name) {
                if !name.is_empty() {
                    exports.push(name.to_string());
                }
            }
        }
    }
    for symbol in elf.dynsyms.iter() {
        if symbol.st_shndx != 0 {
            if let Some(name) = elf.dynstrtab.get_at(symbol.st_name) {
                if !name.is_empty() {
                    exports.push(name.to_string());
                }
            }
        }
    }

    Parsed {
        file_type: if elf.is_64 {
            "ELF64 executable".to_string()
        } else {
            "ELF32 executable".to_string()
        },
        arch,
        bits,
        endian: if elf.little_endian { "little" } else { "big" }.to_string(),
        entry: Some(elf.entry).filter(|value| *value != 0),
        image_base,
        timestamp: None,
        sections,
        imports,
        exports,
        executable,
    }
}

fn parse_macho(macho: &goblin::mach::MachO, data: &[u8]) -> Parsed {
    let bits = if macho.is_64 { 64 } else { 32 };
    let arch = match macho.header.cputype() {
        0x0100_0007 => "x86-64",
        0x0100_000c => "ARM64",
        7 => "x86",
        12 => "ARM",
        18 => "PowerPC",
        _ => "Unknown",
    }
    .to_string();

    let mut sections = Vec::new();
    let mut executable = Vec::new();
    for segment in &macho.segments {
        let name = segment.name().unwrap_or("segment").to_string();
        let read = segment.initprot & 1 != 0;
        let write = segment.initprot & 2 != 0;
        let exec = segment.initprot & 4 != 0;
        if exec && segment.filesize > 0 {
            executable.push((segment.vmaddr, segment.fileoff, segment.filesize));
        }
        sections.push(SectionInfo {
            name,
            address: segment.vmaddr,
            virtual_size: segment.vmsize,
            raw_size: segment.filesize,
            offset: segment.fileoff,
            entropy: entropy(slice(data, segment.fileoff, segment.filesize)),
            perms: perms(read, write, exec),
            executable: exec,
            writable: write,
        });
    }

    let mut imports = Vec::new();
    if let Ok(list) = macho.imports() {
        for import in list {
            imports.push(ImportInfo {
                library: import.dylib.to_string(),
                name: import.name.to_string(),
            });
        }
    }

    let mut exports = Vec::new();
    if let Ok(list) = macho.exports() {
        for export in list {
            if !export.name.is_empty() {
                exports.push(export.name.to_string());
            }
        }
    }

    Parsed {
        file_type: if macho.is_64 {
            "Mach-O 64-bit".to_string()
        } else {
            "Mach-O 32-bit".to_string()
        },
        arch,
        bits,
        endian: if macho.little_endian { "little" } else { "big" }.to_string(),
        entry: Some(macho.entry).filter(|value| *value != 0),
        image_base: 0,
        timestamp: None,
        sections,
        imports,
        exports,
        executable,
    }
}

fn generic(file_type: &str, arch: &str, bits: u32) -> Parsed {
    Parsed {
        file_type: file_type.to_string(),
        arch: arch.to_string(),
        bits,
        endian: "little".to_string(),
        ..Parsed::default()
    }
}

fn generic_unknown(magic: u64, data: &[u8]) -> Parsed {
    let file_type = if data.starts_with(b"MZ") {
        "DOS / PE image (header parse failed)".to_string()
    } else if data.starts_with(b"\x7fELF") {
        "ELF image (header parse failed)".to_string()
    } else if data.starts_with(b"PK\x03\x04") {
        "ZIP / JAR / APK archive".to_string()
    } else if data.starts_with(b"\x1f\x8b") {
        "Gzip archive".to_string()
    } else if data.starts_with(b"%PDF") {
        "PDF document".to_string()
    } else if data.starts_with(b"\x89PNG") {
        "PNG image".to_string()
    } else if is_text(data) {
        "Text / script".to_string()
    } else if magic != 0 {
        format!("Unrecognized binary (magic 0x{magic:08x})")
    } else {
        "Raw data".to_string()
    };
    generic(&file_type, "Unknown", 0)
}

fn disassemble(parsed: &Parsed, data: &[u8]) -> (Vec<DisasmLine>, Vec<CallTarget>) {
    if parsed.bits != 32 && parsed.bits != 64 {
        return (Vec::new(), Vec::new());
    }

    let mut lines: Vec<DisasmLine> = Vec::new();
    let mut calls: HashMap<u64, u32> = HashMap::new();
    let mut budget = DISASM_BYTE_BUDGET;

    for &(address, offset, size) in &parsed.executable {
        if budget == 0 || lines.len() >= MAX_DISASM_LINES {
            break;
        }
        let bytes = slice(data, offset, size.min(budget as u64));
        if bytes.is_empty() {
            continue;
        }
        budget = budget.saturating_sub(bytes.len());

        let mut decoder = Decoder::with_ip(parsed.bits, bytes, address, DecoderOptions::NONE);
        let mut instruction = Instruction::default();
        let mut formatter = IntelFormatter::new();

        while decoder.can_decode() && lines.len() < MAX_DISASM_LINES {
            let position = decoder.position();
            decoder.decode_out(&mut instruction);
            let length = instruction.len().max(1);
            let end = (position + length).min(bytes.len());
            let raw = &bytes[position..end];

            let mut text = String::new();
            formatter.format(&instruction, &mut text);

            let flow = instruction.flow_control();
            let (kind, target) = match flow {
                FlowControl::Call => ("call", branch_target(&instruction)),
                FlowControl::UnconditionalBranch => ("jmp", branch_target(&instruction)),
                FlowControl::ConditionalBranch => ("jcc", branch_target(&instruction)),
                FlowControl::Return => ("ret", None),
                _ => ("other", None),
            };

            if kind == "call" {
                if let Some(target) = target {
                    if in_executable(parsed, target) {
                        *calls.entry(target).or_insert(0) += 1;
                    }
                }
            }

            lines.push(DisasmLine {
                address: instruction.ip(),
                bytes: raw
                    .iter()
                    .map(|byte| format!("{byte:02x}"))
                    .collect::<Vec<_>>()
                    .join(" "),
                text,
                target,
                kind: kind.to_string(),
            });
        }
    }

    let index: BTreeMap<u64, usize> = lines
        .iter()
        .enumerate()
        .map(|(position, line)| (line.address, position))
        .collect();

    let mut targets: Vec<CallTarget> = calls
        .into_iter()
        .map(|(address, count)| {
            let mut instructions = 0u32;
            if let Some((_, &start)) = index.range(address..).next() {
                let mut cursor = start;
                while cursor < lines.len() && cursor < start + 300 {
                    instructions += 1;
                    if lines[cursor].kind == "ret" {
                        break;
                    }
                    cursor += 1;
                }
            }
            CallTarget {
                address,
                calls: count,
                instructions,
            }
        })
        .collect();
    targets.sort_by(|a, b| b.calls.cmp(&a.calls).then(a.address.cmp(&b.address)));
    targets.truncate(300);

    (lines, targets)
}

fn branch_target(instruction: &Instruction) -> Option<u64> {
    let target = instruction.near_branch_target();
    if target == 0 {
        None
    } else {
        Some(target)
    }
}

fn in_executable(parsed: &Parsed, address: u64) -> bool {
    parsed
        .executable
        .iter()
        .any(|(start, _, size)| address >= *start && address < start.saturating_add(*size))
}

fn extract_strings(data: &[u8]) -> (Vec<StringInfo>, u32) {
    let mut strings = Vec::new();
    let mut count = 0u32;

    let mut index = 0usize;
    while index < data.len() {
        if is_ascii_printable(data[index]) {
            let start = index;
            while index < data.len() && is_ascii_printable(data[index]) {
                index += 1;
            }
            let length = index - start;
            if length >= 4 {
                count += 1;
                if strings.len() < MAX_STRINGS {
                    let end = (start + length).min(start + 512);
                    strings.push(StringInfo {
                        offset: start as u64,
                        text: String::from_utf8_lossy(&data[start..end]).to_string(),
                        kind: "ascii".to_string(),
                    });
                }
            }
        } else {
            index += 1;
        }
    }

    let mut index = 0usize;
    while index + 1 < data.len() {
        if is_ascii_printable(data[index]) && data[index + 1] == 0 {
            let start = index;
            let mut cursor = index;
            while cursor + 1 < data.len()
                && is_ascii_printable(data[cursor])
                && data[cursor + 1] == 0
            {
                cursor += 2;
            }
            let length = (cursor - start) / 2;
            if length >= 4 {
                count += 1;
                if strings.len() < MAX_STRINGS {
                    let mut text = String::new();
                    let mut read = start;
                    while read < cursor && text.len() < 512 {
                        text.push(data[read] as char);
                        read += 2;
                    }
                    strings.push(StringInfo {
                        offset: start as u64,
                        text,
                        kind: "utf16".to_string(),
                    });
                }
                index = cursor;
                continue;
            }
            index = cursor.max(start + 2);
        } else {
            index += 1;
        }
    }

    (strings, count)
}

fn build_indicators(
    parsed: &Parsed,
    file_entropy: f64,
    file_size: u64,
    strings: &[StringInfo],
) -> Vec<Indicator> {
    let mut out = Vec::new();
    let imports: Vec<String> = parsed
        .imports
        .iter()
        .map(|import| import.name.to_ascii_lowercase())
        .collect();

    let injection = matched_group(
        &imports,
        &[
            "virtualallocex",
            "writeprocessmemory",
            "createremotethread",
            "ntunmapviewofsection",
            "setthreadcontext",
            "queueuserapc",
            "openprocess",
            "rtlcreateuserthread",
        ],
    );
    if !injection.is_empty() {
        out.push(Indicator {
            severity: "high".to_string(),
            title: "Process injection API surface".to_string(),
            detail: format!(
                "Imports used for allocating or writing memory in another process: {}.",
                injection.join(", ")
            ),
        });
    }

    let anti_analysis = matched_group(
        &imports,
        &[
            "isdebuggerpresent",
            "checkremotedebuggerpresent",
            "ntqueryinformationprocess",
            "outputdebugstringa",
            "outputdebugstringw",
            "gettickcount",
            "queryperformancecounter",
            "setunhandledexceptionfilter",
        ],
    );
    if !anti_analysis.is_empty() {
        out.push(Indicator {
            severity: "medium".to_string(),
            title: "Anti-analysis signals".to_string(),
            detail: format!(
                "Debugger, timing, or exception-based checks: {}.",
                anti_analysis.join(", ")
            ),
        });
    }

    let network = matched_group(
        &imports,
        &[
            "wsastartup",
            "socket",
            "connect",
            "send",
            "recv",
            "winhttpopen",
            "winhttpconnect",
            "internetopen",
            "internetopenurl",
            "httpsendrequest",
            "urldownloadtofile",
            "ws2_32.dll",
        ],
    );
    if !network.is_empty() {
        out.push(Indicator {
            severity: "medium".to_string(),
            title: "Networking capability".to_string(),
            detail: format!(
                "Imports that can reach the network: {}.",
                network.join(", ")
            ),
        });
    }

    let persistence = matched_group(
        &imports,
        &[
            "regsetvalueexa",
            "regsetvalueexw",
            "regcreatekeyexa",
            "regcreatekeyexw",
            "createservicea",
            "createservicew",
            "startservicea",
            "startservicew",
        ],
    );
    if !persistence.is_empty() {
        out.push(Indicator {
            severity: "medium".to_string(),
            title: "Persistence or configuration writes".to_string(),
            detail: format!(
                "Registry and service APIs that can persist state: {}.",
                persistence.join(", ")
            ),
        });
    }

    let capture = matched_group(
        &imports,
        &[
            "setwindowshookexa",
            "setwindowshookexw",
            "getasynckeystate",
            "getkeystate",
            "bitblt",
            "getdc",
            "getwindowdc",
        ],
    );
    if !capture.is_empty() {
        out.push(Indicator {
            severity: "medium".to_string(),
            title: "Input capture or screen access".to_string(),
            detail: format!(
                "Keyboard, hook, or screen-capture imports: {}.",
                capture.join(", ")
            ),
        });
    }

    let privilege = matched_group(
        &imports,
        &[
            "adjusttokenprivileges",
            "openprocesstoken",
            "lookupprivilegevaluea",
            "lookupprivilegevaluew",
            "impersonateloggedonuser",
        ],
    );
    if !privilege.is_empty() {
        out.push(Indicator {
            severity: "medium".to_string(),
            title: "Privilege manipulation".to_string(),
            detail: format!(
                "Token and privilege APIs: {}.",
                privilege.join(", ")
            ),
        });
    }

    let dynamic = matched_group(
        &imports,
        &[
            "loadlibrarya",
            "loadlibraryw",
            "getprocaddress",
            "virtualprotect",
            "virtualalloc",
        ],
    );
    if !dynamic.is_empty() {
        out.push(Indicator {
            severity: "info".to_string(),
            title: "Runtime code loading".to_string(),
            detail: format!(
                "Dynamic loading or memory-permission imports: {}.",
                dynamic.join(", ")
            ),
        });
    }

    if parsed
        .imports
        .iter()
        .any(|import| import.library.eq_ignore_ascii_case("mscoree.dll"))
    {
        out.push(Indicator {
            severity: "info".to_string(),
            title: "Managed .NET assembly".to_string(),
            detail: "Imports mscoree.dll, so the real logic likely lives in managed IL rather than native code. Use a .NET decompiler for method bodies.".to_string(),
        });
    }

    if parsed
        .sections
        .iter()
        .any(|section| section.name.to_ascii_lowercase().contains("upx"))
    {
        out.push(Indicator {
            severity: "high".to_string(),
            title: "UPX-like section names".to_string(),
            detail: "Section names resemble UPX packing. Unpack before trusting imports or disassembly; packed stubs usually expose little behavior.".to_string(),
        });
    }

    let rwx: Vec<&str> = parsed
        .sections
        .iter()
        .filter(|section| section.executable && section.writable)
        .map(|section| section.name.as_str())
        .collect();
    if !rwx.is_empty() {
        out.push(Indicator {
            severity: "high".to_string(),
            title: "Writable and executable sections".to_string(),
            detail: format!(
                "Sections marked RWX: {}. This is common in packers, JIT engines, and self-modifying code.",
                rwx.join(", ")
            ),
        });
    }

    let high_entropy: Vec<String> = parsed
        .sections
        .iter()
        .filter(|section| section.executable && section.raw_size >= 4096 && section.entropy > 7.35)
        .map(|section| format!("{} ({:.2})", section.name, section.entropy))
        .collect();
    if !high_entropy.is_empty() {
        out.push(Indicator {
            severity: "medium".to_string(),
            title: "High-entropy executable code".to_string(),
            detail: format!(
                "Executable sections with entropy above 7.35 bits/byte: {}. Encrypted or compressed code is likely.",
                high_entropy.join(", ")
            ),
        });
    }

    if file_entropy > 7.2 {
        out.push(Indicator {
            severity: "medium".to_string(),
            title: "High overall entropy".to_string(),
            detail: format!(
                "Whole-file entropy is {file_entropy:.2} bits/byte. The sample is probably packed, encrypted, or compressed."
            ),
        });
    }

    if parsed.imports.len() < 5 && file_size > 1024 * 1024 {
        out.push(Indicator {
            severity: "info".to_string(),
            title: "Very small import table".to_string(),
            detail: format!(
                "Only {} imports were recovered from a {} file. Expect static linking, packing, or a loader that resolves APIs at runtime.",
                parsed.imports.len(),
                format_bytes(file_size)
            ),
        });
    }

    let mut urls = Vec::new();
    let mut ips = Vec::new();
    let mut encoded = 0usize;
    let mut commands = Vec::new();
    let mut registry = Vec::new();
    for item in strings.iter().take(MAX_STRINGS) {
        let lower = item.text.to_ascii_lowercase();
        if (lower.contains("http://") || lower.contains("https://")) && urls.len() < 5 {
            urls.push(clip(&item.text, 110));
        }
        if ips.len() < 5 && looks_like_ip(&item.text) {
            ips.push(clip(&item.text, 60));
        }
        if item.text.len() >= 60
            && item
                .text
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || matches!(c, '+' | '/' | '='))
        {
            encoded += 1;
        }
        if commands.len() < 4
            && (lower.contains("powershell")
                || lower.contains("cmd.exe /c")
                || lower.contains("wscript")
                || lower.contains("mshta"))
        {
            commands.push(clip(&item.text, 110));
        }
        if registry.len() < 4
            && (lower.contains("hkey_local_machine")
                || lower.contains("hkey_current_user")
                || lower.contains("\\registry\\"))
        {
            registry.push(clip(&item.text, 110));
        }
    }

    if !urls.is_empty() || !ips.is_empty() {
        let mut evidence = Vec::new();
        if !urls.is_empty() {
            evidence.push(format!("URLs: {}", urls.join(" | ")));
        }
        if !ips.is_empty() {
            evidence.push(format!("IPv4 literals: {}", ips.join(", ")));
        }
        out.push(Indicator {
            severity: "medium".to_string(),
            title: "Network indicators in strings".to_string(),
            detail: evidence.join(". "),
        });
    }

    if !commands.is_empty() {
        out.push(Indicator {
            severity: "high".to_string(),
            title: "Command execution strings".to_string(),
            detail: format!("Embedded command strings: {}.", commands.join(" | ")),
        });
    }

    if !registry.is_empty() {
        out.push(Indicator {
            severity: "info".to_string(),
            title: "Registry paths in strings".to_string(),
            detail: format!("Registry references: {}.", registry.join(" | ")),
        });
    }

    if encoded >= 3 {
        out.push(Indicator {
            severity: "info".to_string(),
            title: "Encoded-looking blobs".to_string(),
            detail: format!(
                "{encoded} long strings look base64-like. Inspect them for embedded payloads, keys, or configuration."
            ),
        });
    }

    out.sort_by(|a, b| {
        severity_rank(&a.severity)
            .cmp(&severity_rank(&b.severity))
            .then(a.title.cmp(&b.title))
    });
    out
}

pub fn build_context(report: &BinaryReport) -> String {
    let mut out = String::new();
    let _ = writeln!(out, "IDENTITY");
    let _ = writeln!(out, "name: {}", report.name);
    let _ = writeln!(out, "path: {}", report.path);
    let _ = writeln!(
        out,
        "size: {} bytes ({})",
        report.size,
        format_bytes(report.size)
    );
    let _ = writeln!(out, "type: {}", report.file_type);
    let _ = writeln!(
        out,
        "architecture: {}-bit {} ({})",
        report.bits, report.arch, report.endian
    );
    let _ = writeln!(
        out,
        "entry point: {}",
        match report.entry {
            Some(value) => format!("{value:#x}"),
            None => "unknown".to_string(),
        }
    );
    let _ = writeln!(out, "image base: {:#x}", report.image_base);
    let _ = writeln!(
        out,
        "timestamp: {}",
        match report.timestamp {
            Some(value) => format!("{value} (unix)"),
            None => "unknown".to_string(),
        }
    );
    let _ = writeln!(out, "sha256: {}", report.sha256);
    let _ = writeln!(out, "md5: {}", report.md5);
    let _ = writeln!(
        out,
        "overall entropy: {:.2} bits/byte (0-8)",
        report.entropy
    );

    let _ = writeln!(out, "\nHEURISTIC FINDINGS");
    if report.indicators.is_empty() {
        let _ = writeln!(out, "- none");
    }
    for indicator in &report.indicators {
        let _ = writeln!(
            out,
            "- [{}] {}: {}",
            indicator.severity, indicator.title, indicator.detail
        );
    }

    let _ = writeln!(out, "\nSECTIONS ({} total)", report.sections.len());
    for section in report.sections.iter().take(40) {
        let _ = writeln!(
            out,
            "- {} address={:#x} vsize={} rawsize={} fileoff={:#x} entropy={:.2} perms={}",
            if section.name.is_empty() {
                "(unnamed)"
            } else {
                &section.name
            },
            section.address,
            section.virtual_size,
            section.raw_size,
            section.offset,
            section.entropy,
            section.perms
        );
    }

    let _ = writeln!(
        out,
        "\nIMPORTS ({} total, showing up to 150)",
        report.import_count
    );
    for import in report.imports.iter().take(150) {
        let _ = writeln!(out, "- {}!{}", import.library, import.name);
    }

    let _ = writeln!(
        out,
        "\nEXPORTS ({} total, showing up to 80)",
        report.exports.len()
    );
    for export in report.exports.iter().take(80) {
        let _ = writeln!(out, "- {export}");
    }

    let _ = writeln!(out, "\nNOTABLE STRINGS");
    let mut shown = 0usize;
    for item in &report.strings {
        let lower = item.text.to_ascii_lowercase();
        let notable = lower.contains("http://")
            || lower.contains("https://")
            || looks_like_ip(&item.text)
            || lower.contains("powershell")
            || lower.contains("cmd.exe")
            || lower.contains("hkey_")
            || lower.contains("\\registry\\")
            || lower.contains("mimikatz")
            || lower.contains("lsass")
            || lower.contains(".dll")
            || lower.contains(".exe")
            || lower.contains("mutex")
            || lower.contains("token")
            || item.text.len() >= 60;
        if !notable {
            continue;
        }
        let _ = writeln!(
            out,
            "- [{}] {:#x}: {}",
            item.kind,
            item.offset,
            clip(&item.text, 160)
        );
        shown += 1;
        if shown >= 120 {
            break;
        }
    }
    if shown == 0 {
        for item in report.strings.iter().take(30) {
            let _ = writeln!(out, "- [{}] {:#x}: {}", item.kind, item.offset, clip(&item.text, 120));
        }
    }

    let _ = writeln!(
        out,
        "\nCALL TARGETS ({} total, showing up to 60 by call count)",
        report.call_targets.len()
    );
    for target in report.call_targets.iter().take(60) {
        let _ = writeln!(
            out,
            "- {:#x}: {} direct calls, ~{} instructions to next ret",
            target.address, target.calls, target.instructions
        );
    }

    let _ = writeln!(
        out,
        "\nDISASSEMBLY SAMPLE ({} decoded lines total; first 260 shown)",
        report.disassembly.len()
    );
    for line in report.disassembly.iter().take(260) {
        let _ = writeln!(
            out,
            "{:016x}  {:<24}  {}",
            line.address, line.bytes, line.text
        );
    }

    let _ = writeln!(out, "\nWARNINGS");
    if report.warnings.is_empty() {
        let _ = writeln!(out, "- none");
    }
    for warning in &report.warnings {
        let _ = writeln!(out, "- {warning}");
    }

    truncate_chars(out, MAX_CONTEXT_CHARS)
}

fn matched_group(imports: &[String], needles: &[&str]) -> Vec<String> {
    let mut found = Vec::new();
    for needle in needles {
        for import in imports {
            if import == needle {
                let label = import.clone();
                if !found.contains(&label) {
                    found.push(label);
                }
            }
        }
    }
    found
}

fn severity_rank(severity: &str) -> u8 {
    match severity {
        "high" => 0,
        "medium" => 1,
        _ => 2,
    }
}

fn looks_like_ip(text: &str) -> bool {
    let trimmed = text.trim_matches(|c: char| !c.is_ascii_digit() && c != '.');
    if trimmed.len() > 15 {
        return false;
    }
    let parts: Vec<&str> = trimmed.split('.').collect();
    parts.len() == 4
        && parts.iter().all(|part| {
            !part.is_empty()
                && part.len() <= 3
                && part
                    .parse::<u16>()
                    .map(|value| value <= 255)
                    .unwrap_or(false)
        })
}

fn perms(read: bool, write: bool, exec: bool) -> String {
    format!(
        "{}{}{}",
        if read { "R" } else { "-" },
        if write { "W" } else { "-" },
        if exec { "X" } else { "-" }
    )
}

fn entropy(bytes: &[u8]) -> f64 {
    if bytes.is_empty() {
        return 0.0;
    }
    let mut counts = [0u64; 256];
    for byte in bytes {
        counts[*byte as usize] += 1;
    }
    let length = bytes.len() as f64;
    counts
        .iter()
        .filter(|count| **count > 0)
        .map(|count| {
            let probability = *count as f64 / length;
            -probability * probability.log2()
        })
        .sum()
}

fn is_ascii_printable(byte: u8) -> bool {
    (0x20..=0x7e).contains(&byte)
}

fn is_text(data: &[u8]) -> bool {
    let sample = &data[..data.len().min(4096)];
    if sample.is_empty() {
        return false;
    }
    let printable = sample
        .iter()
        .filter(|byte| **byte == b'\n' || **byte == b'\r' || **byte == b'\t' || is_ascii_printable(**byte))
        .count();
    printable as f64 / sample.len() as f64 > 0.9
}

fn slice<'a>(data: &'a [u8], offset: u64, size: u64) -> &'a [u8] {
    let start = offset as usize;
    if start >= data.len() || size == 0 {
        return &[];
    }
    let end = start.saturating_add(size as usize).min(data.len());
    &data[start..end]
}

fn hex(bytes: &[u8]) -> String {
    let mut out = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        let _ = write!(out, "{byte:02x}");
    }
    out
}

fn format_bytes(size: u64) -> String {
    const UNITS: [&str; 5] = ["B", "KB", "MB", "GB", "TB"];
    let mut value = size as f64;
    let mut unit = 0usize;
    while value >= 1024.0 && unit < UNITS.len() - 1 {
        value /= 1024.0;
        unit += 1;
    }
    if unit == 0 {
        format!("{size} B")
    } else {
        format!("{value:.1} {}", UNITS[unit])
    }
}

fn clip(text: &str, max: usize) -> String {
    let cleaned: String = text
        .chars()
        .map(|c| if c.is_control() { ' ' } else { c })
        .collect();
    truncate_chars(cleaned, max)
}

fn truncate_chars(text: String, max: usize) -> String {
    if text.chars().count() <= max {
        return text;
    }
    let mut out: String = text.chars().take(max).collect();
    out.push_str("\n... [truncated]");
    out
}
