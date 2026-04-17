import Foundation

/// 24-hour accountability delay — iOS version.
/// State stored in UserDefaults(suiteName:) shared with the extension.
enum DelayGuard {

    private static let suite   = UserDefaults(suiteName: "group.com.blockcorn")
    private static let key     = "disable_requested_at"
    private static let delayS: TimeInterval = 24 * 60 * 60

    static var isPending: Bool {
        guard let ts = requestedAt else { return false }
        return !isExpired(ts)
    }

    static var isReadyToDisable: Bool {
        guard let ts = requestedAt else { return false }
        return isExpired(ts)
    }

    static func secondsRemaining() -> Int {
        guard let ts = requestedAt else { return 0 }
        let remaining = delayS - Date().timeIntervalSince(ts)
        return remaining > 0 ? Int(remaining) : 0
    }

    static func requestDisable() {
        suite?.set(Date().timeIntervalSince1970, forKey: key)
        suite?.synchronize()
    }

    static func clear() {
        suite?.removeObject(forKey: key)
        suite?.synchronize()
    }

    private static var requestedAt: Date? {
        let ts = suite?.double(forKey: key) ?? 0
        return ts > 0 ? Date(timeIntervalSince1970: ts) : nil
    }

    private static func isExpired(_ date: Date) -> Bool {
        Date().timeIntervalSince(date) >= delayS
    }
}
