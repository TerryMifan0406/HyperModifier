// Generated from the verified native ELF pair, never a launcher version.
#pragma once
#include <cstdint>
namespace clock_glass {
struct Contract { uintptr_t rva; uint32_t size; uint64_t hash; };
inline constexpr unsigned char kMamlBuildId[] = {0xdc,0x79,0x5c,0x18,0xe8,0xfe,0xe6,0x02,0x94,0xeb,0xb8,0x04,0x0b,0xe9,0x2b,0xc9,0x4e,0xed,0x4c,0x05};
inline constexpr Contract kMamlContracts[] = {
    {0x48a6d0, 0x3b0, UINT64_C(0xfb0aee10c98a0cac)}, // TextScope: _RNvMs3_NtNtCs6cyGIkCd63u_9rust_maml8elements19text_screen_elementNtB5_17TextScreenElement9draw_text
    {0x41b6b4, 0x14, UINT64_C(0x34a86b28b879b09)}, // Name: _RNvMNtCslmOlyzQgMlu_11lean_string4reprNtB2_4Repr6as_str.4279
    {0x48668c, 0x28, UINT64_C(0xcf3c0932431ea160)}, // NameStorage: _RNvMs3_NtNtCs6cyGIkCd63u_9rust_maml8elements19text_screen_elementNtB5_17TextScreenElement16marquee_log_name
    {0x5eb2b0, 0x24, UINT64_C(0xf675e03b749bfc2f)}, // NameDecode: OUTLINED_FUNCTION_9294
    {0x5e2e24, 0xc, UINT64_C(0x6530a564bbf26450)}, // NamePointer: OUTLINED_FUNCTION_7054
};
inline constexpr uintptr_t kTextScope = kMamlContracts[0].rva;
inline constexpr uintptr_t kName = kMamlContracts[1].rva;
inline constexpr uintptr_t kNameStorage = kMamlContracts[2].rva;
inline constexpr uintptr_t kNameDecode = kMamlContracts[3].rva;
inline constexpr uintptr_t kNamePointer = kMamlContracts[4].rva;
inline constexpr unsigned char kFlutterBuildId[] = {0xc0,0x14,0xa1,0x4b,0xb9,0xdd,0xab,0x44,0x31,0xa8,0x87,0xc9,0x8c,0x6d,0x56,0xce,0x30,0x5b,0x05,0x73};
inline constexpr Contract kFlutterContracts[] = {
    {0xb373e4, 0x5a8, UINT64_C(0xddb790848a7f2723)}, // Paragraph: _ZN3txt12_GLOBAL__N_127DisplayListParagraphPainter12drawTextBlobERK5sk_spI10SkTextBlobEffRKNSt3_fl7variantIJ7SkPaintiEEE
    {0x7e0328, 0xbb4, UINT64_C(0xf1186952fb8f8452)}, // SetPaint: _ZN7flutter18DisplayListBuilder22SetAttributesFromPaintERKNS_7DlPaintENS_25DisplayListAttributeFlagsE
    {0x7d1ea4, 0x31c, UINT64_C(0x6d7f6ac5e221cd)}, // DrawRect: _ZN7flutter18DisplayListBuilder8drawRectERKN8impeller5TRectIfEE
    {0xc46b88, 0x1328, UINT64_C(0xd768ea982a88140f)}, // ReplayRect: _ZN8impeller16DlDispatcherBase8drawRectERKNS_5TRectIfEE
    {0xc310e8, 0x254, UINT64_C(0xca42ba987248cf06)}, // ConsumePending: _ZN8impeller13PassBlurState17ConsumePendingAHBEv
    {0x7cbb94, 0xac, UINT64_C(0xa99ce81058c13ee1)}, // SaveLayer: _ZN7flutter18DisplayListBuilder9SaveLayerERKNSt3_fl8optionalIN8impeller5TRectIfEEEEPKNS_7DlPaintEPKNS_13DlImageFilterENS2_IlEENS2_ImEE
    {0x7d0c40, 0xa58, UINT64_C(0xd55275c417fea722)}, // SaveLayerBody: _ZN7flutter18DisplayListBuilder9saveLayerERKN8impeller5TRectIfEENS_16SaveLayerOptionsEPKNS_13DlImageFilterENSt3_fl8optionalIlEENSB_ImEE
    {0x7ca500, 0xdf4, UINT64_C(0xfeb784b84f35a41a)}, // Restore: _ZN7flutter18DisplayListBuilder7RestoreEv
};
inline constexpr uintptr_t kParagraph = kFlutterContracts[0].rva;
inline constexpr uintptr_t kSetPaint = kFlutterContracts[1].rva;
inline constexpr uintptr_t kDrawRect = kFlutterContracts[2].rva;
inline constexpr uintptr_t kReplayRect = kFlutterContracts[3].rva;
inline constexpr uintptr_t kConsumePending = kFlutterContracts[4].rva;
inline constexpr uintptr_t kSaveLayer = kFlutterContracts[5].rva;
inline constexpr uintptr_t kSaveLayerBody = kFlutterContracts[6].rva;
inline constexpr uintptr_t kRestore = kFlutterContracts[7].rva;
}
