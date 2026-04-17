import SwiftUI
import NetworkExtension

struct ContentView: View {

    @StateObject private var vm = ViewModel()

    var body: some View {
        ZStack {
            Color(hex: "#0F0F13").ignoresSafeArea()

            VStack(spacing: 24) {
                Text("🌽")
                    .font(.system(size: 72))

                Text("BlockCorn")
                    .font(.title.bold())
                    .foregroundColor(Color(hex: "#E4E4F0"))

                // Status badge
                HStack(spacing: 8) {
                    Circle()
                        .fill(vm.isEnabled ? Color(hex: "#22C55E") : Color(hex: "#EF4444"))
                        .frame(width: 10, height: 10)
                    Text(vm.isEnabled ? "Фильтр активен" : "Фильтр отключён")
                        .foregroundColor(Color(hex: "#E4E4F0"))
                        .font(.subheadline.weight(.semibold))
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
                .background(Color(hex: "#1A1A24"))
                .cornerRadius(20)

                // Toggle button
                Button(action: vm.handleToggle) {
                    Text(vm.isEnabled ? "Отключить фильтр…" : "Включить фильтр")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(width: 240, height: 52)
                        .background(Color(hex: "#6C34EB"))
                        .cornerRadius(12)
                }
                .disabled(vm.isBusy)

                if let info = vm.infoMessage {
                    Text(info)
                        .font(.caption)
                        .foregroundColor(Color(hex: "#7C7C9A"))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
            }
        }
        .alert("Введите PIN", isPresented: $vm.showingPinAlert) {
            SecureField("PIN", text: $vm.pinInput)
            Button("Подтвердить") { vm.confirmDisableWithPin() }
            Button("Отмена", role: .cancel) {}
        }
        .sheet(isPresented: $vm.showingSetPin) {
            SetPinView()
        }
        .onAppear { vm.onAppear() }
    }
}

// MARK: - ViewModel

@MainActor
final class ViewModel: ObservableObject {

    @Published var isEnabled    = false
    @Published var isBusy       = false
    @Published var infoMessage: String?
    @Published var showingPinAlert = false
    @Published var showingSetPin   = false
    @Published var pinInput        = ""

    func onAppear() {
        if !PinManager.shared.isSet {
            showingSetPin = true
        }
        refreshStatus()
    }

    func handleToggle() {
        if isEnabled {
            initiateDisable()
        } else {
            enableFilter()
        }
    }

    func confirmDisableWithPin() {
        guard PinManager.shared.verify(pinInput) else {
            infoMessage = "Неверный PIN"
            pinInput = ""
            return
        }
        pinInput = ""
        DelayGuard.clear()
        disableFilter()
    }

    // MARK: Private

    private func enableFilter() {
        isBusy = true
        NEDNSProxyManager.shared().loadFromPreferences { [weak self] _ in
            guard let self = self else { return }
            let mgr = NEDNSProxyManager.shared()
            let proto = NEDNSProxyProviderProtocol()
            proto.providerBundleIdentifier = "com.blockcorn.dns-proxy"
            proto.serverAddress = "blockcorn"
            mgr.providerProtocol = proto
            mgr.isEnabled = true
            mgr.localizedDescription = "BlockCorn DNS Filter"
            mgr.saveToPreferences { _ in
                mgr.loadFromPreferences { _ in
                    self.isEnabled = true
                    self.isBusy = false
                    self.infoMessage = nil
                }
            }
        }
    }

    private func disableFilter() {
        isBusy = true
        NEDNSProxyManager.shared().loadFromPreferences { [weak self] _ in
            NEDNSProxyManager.shared().isEnabled = false
            NEDNSProxyManager.shared().saveToPreferences { _ in
                self?.isEnabled = false
                self?.isBusy = false
                self?.infoMessage = nil
            }
        }
    }

    private func initiateDisable() {
        if DelayGuard.isReadyToDisable() {
            showingPinAlert = true
        } else if DelayGuard.isPending() {
            let secs  = DelayGuard.secondsRemaining()
            let hours = secs / 3600
            let mins  = (secs % 3600) / 60
            infoMessage = "Запрос уже отправлен.\nОсталось: \(hours)ч \(mins)мин."
        } else {
            DelayGuard.requestDisable()
            let deadline = Date().addingTimeInterval(24 * 60 * 60)
            let fmt = DateFormatter()
            fmt.dateStyle = .short
            fmt.timeStyle = .short
            infoMessage = "Запрос принят.\nОтключение доступно: \(fmt.string(from: deadline)).\nПотребуется PIN."
        }
    }

    private func refreshStatus() {
        NEDNSProxyManager.shared().loadFromPreferences { [weak self] _ in
            self?.isEnabled = NEDNSProxyManager.shared().isEnabled
        }
    }
}

// MARK: - Set PIN view

struct SetPinView: View {
    @State private var pin        = ""
    @State private var confirm    = ""
    @State private var errorMsg   = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color(hex: "#0F0F13").ignoresSafeArea()
                VStack(spacing: 16) {
                    Text("Установите PIN")
                        .font(.title2.bold()).foregroundColor(Color(hex: "#E4E4F0"))
                    Text("PIN потребуется для отключения\nфильтра после 24-часовой задержки")
                        .font(.subheadline).foregroundColor(Color(hex: "#7C7C9A"))
                        .multilineTextAlignment(.center)
                    SecureField("PIN (мин. 4 символа)", text: $pin)
                        .textFieldStyle(.roundedBorder).frame(width: 240)
                    SecureField("Подтвердите PIN", text: $confirm)
                        .textFieldStyle(.roundedBorder).frame(width: 240)
                    if !errorMsg.isEmpty {
                        Text(errorMsg).foregroundColor(.red).font(.caption)
                    }
                    Button("Сохранить PIN") {
                        savePIN()
                    }
                    .frame(width: 240, height: 48)
                    .background(Color(hex: "#6C34EB"))
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func savePIN() {
        guard pin.count >= 4 else { errorMsg = "Минимум 4 символа"; return }
        guard pin == confirm   else { errorMsg = "PIN не совпадает"; return }
        do {
            try PinManager.shared.setPin(pin)
            dismiss()
        } catch {
            errorMsg = error.localizedDescription
        }
    }
}

// MARK: - Color hex helper

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r = Double((int >> 16) & 0xFF) / 255
        let g = Double((int >> 8)  & 0xFF) / 255
        let b = Double(int & 0xFF)          / 255
        self.init(red: r, green: g, blue: b)
    }
}
