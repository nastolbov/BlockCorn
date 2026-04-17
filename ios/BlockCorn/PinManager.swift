import Foundation
import Security
import CryptoKit

/// PIN management using the iOS Keychain.
/// The PIN hash (SHA-256 + salt, server-side bcrypt would require a Swift bcrypt library)
/// is stored in the Keychain so it survives reinstallation if backup is allowed.
final class PinManager {

    static let shared = PinManager()
    private let service = "com.blockcorn.pin"
    private let account = "pin_hash"

    private init() {}

    var isSet: Bool { loadHash() != nil }

    func setPin(_ pin: String) throws {
        guard pin.count >= 4 else {
            throw BlockCornError.pinTooShort
        }
        let hash = hashPin(pin)
        try saveHash(hash)
    }

    func verify(_ pin: String) -> Bool {
        guard let stored = loadHash() else { return false }
        return hashPin(pin) == stored
    }

    func clearPin() {
        let query: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Private

    private func hashPin(_ pin: String) -> String {
        // Salt derived from device identifier so it's device-specific
        let salt = (UIDevice.current.identifierForVendor?.uuidString ?? "blockcorn") + "blockcorn"
        let data  = Data((pin + salt).utf8)
        let digest = SHA256.hash(data: data)
        return digest.compactMap { String(format: "%02x", $0) }.joined()
    }

    private func saveHash(_ hash: String) throws {
        let data  = Data(hash.utf8)
        let query: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String:   data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemDelete(query as CFDictionary)
        let status = SecItemAdd(query as CFDictionary, nil)
        if status != errSecSuccess {
            throw BlockCornError.keychainError(status)
        }
    }

    private func loadHash() -> String? {
        let query: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String:  true,
        ]
        var result: AnyObject?
        SecItemCopyMatching(query as CFDictionary, &result)
        guard let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

enum BlockCornError: Error, LocalizedError {
    case pinTooShort
    case keychainError(OSStatus)

    var errorDescription: String? {
        switch self {
        case .pinTooShort:        return "PIN должен содержать не менее 4 символов"
        case .keychainError(let s): return "Ошибка Keychain: \(s)"
        }
    }
}
