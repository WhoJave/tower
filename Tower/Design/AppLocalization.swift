import Foundation

/// Preferred UI language inside Tower. "system" preserves Apple's per-app
/// language selection (Settings > Apps > Tower > Language) and all other
/// languages already shipped in the main string catalog.
enum AppLocalization {
    static let languagePreferenceKey = "tower.preferredLanguage"
    static let systemLanguagePreference = "system"
    static let simplifiedChinesePreference = "zh-Hans"
    static let englishPreference = "en"

    static var locale: Locale {
        locale(for: UserDefaults.standard.string(forKey: languagePreferenceKey))
    }

    static func locale(for preference: String?) -> Locale {
        switch preference {
        case simplifiedChinesePreference:
            return Locale(identifier: simplifiedChinesePreference)
        case englishPreference:
            return Locale(identifier: englishPreference)
        default:
            guard let identifier = Bundle.main.preferredLocalizations.first else {
                return .autoupdatingCurrent
            }
            return Locale(identifier: identifier)
        }
    }

    static func regionName(
        for countryCode: String,
        fallback: String? = nil,
        locale: Locale = AppLocalization.locale
    ) -> String {
        let normalizedCode = countryCode.uppercased()
        let localizedName = locale.localizedString(forRegionCode: normalizedCode)
            ?? fallback
            ?? normalizedCode

        return compactRegionName(localizedName, for: normalizedCode, locale: locale)
    }

    /// Apple can qualify Hong Kong, Macao and Taiwan with a parent country on
    /// some OS/locale combinations. Tower presents these as peer regions, so
    /// their labels stay short anywhere `regionName` is used.
    static func compactRegionName(
        _ localizedName: String,
        for countryCode: String,
        locale: Locale = AppLocalization.locale
    ) -> String {
        let normalizedCode = countryCode.uppercased()
        guard ["HK", "MO", "TW"].contains(normalizedCode) else {
            return localizedName
        }

        let localeIdentifier = locale.identifier.lowercased()
        if localeIdentifier.hasPrefix("zh") {
            // The script wins over the region. `zh-Hans-HK` is Simplified
            // Chinese as written in Hong Kong, and reading only the region
            // turned it Traditional.
            let usesTraditionalChinese: Bool
            if localeIdentifier.contains("hans") {
                usesTraditionalChinese = false
            } else if localeIdentifier.contains("hant") {
                usesTraditionalChinese = true
            } else {
                usesTraditionalChinese = ["_tw", "-tw", "_hk", "-hk", "_mo", "-mo"]
                    .contains { localeIdentifier.contains($0) }
            }

            switch normalizedCode {
            case "HK": return "香港"
            case "MO": return usesTraditionalChinese ? "澳門" : "澳门"
            case "TW": return usesTraditionalChinese ? "台灣" : "台湾"
            default: break
            }
        }

        let qualifierStart = localizedName.firstIndex { $0 == "(" || $0 == "（" }
        guard let qualifierStart else { return localizedName }
        return localizedName[..<qualifierStart]
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
