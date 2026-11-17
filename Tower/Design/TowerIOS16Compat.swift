import SwiftUI

// iOS 16 compatibility shims for APIs introduced by SwiftUI in iOS 17.
// iOS 17+ preserves the original haptic behavior; iOS 16 skips optional haptics.
enum TowerHaptic {
    case selection
    case success
}

struct TowerUnavailableView: View {
    let title: LocalizedStringKey
    let systemImage: String
    let description: Text?

    init(_ title: LocalizedStringKey, systemImage: String, description: Text? = nil) {
        self.title = title
        self.systemImage = systemImage
        self.description = description
    }

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 32))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.headline)
            if let description {
                description
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding()
    }
}

extension View {
    @ViewBuilder
    func towerGeometryGroup() -> some View {
        if #available(iOS 17.0, *) {
            self.geometryGroup()
        } else {
            self
        }
    }

    @ViewBuilder
    func towerMenuActionDismissBehavior(keepOpen: Bool) -> some View {
        if #available(iOS 16.4, *) {
            self.menuActionDismissBehavior(keepOpen ? .disabled : .enabled)
        } else {
            self
        }
    }

    func towerOnChange<Value: Equatable>(
        of value: Value,
        _ action: @escaping () -> Void
    ) -> some View {
        onChange(of: value) { _ in action() }
    }


    @ViewBuilder
    func towerSensoryFeedback<Value: Equatable>(_ feedback: TowerHaptic, trigger: Value) -> some View {
        if #available(iOS 17.0, *) {
            switch feedback {
            case .selection: self.sensoryFeedback(.selection, trigger: trigger)
            case .success: self.sensoryFeedback(.success, trigger: trigger)
            }
        } else {
            self
        }
    }

    @ViewBuilder
    func towerSensoryFeedback<Value: Equatable>(
        _ feedback: TowerHaptic,
        trigger: Value,
        condition: @escaping (Value, Value) -> Bool
    ) -> some View {
        if #available(iOS 17.0, *) {
            switch feedback {
            case .selection: self.sensoryFeedback(.selection, trigger: trigger, condition: condition)
            case .success: self.sensoryFeedback(.success, trigger: trigger, condition: condition)
            }
        } else {
            self
        }
    }

    // The iOS 17 two-value onChange overload is not available to iOS 16.
    // Preserve its old/new values and 'initial' behavior on older systems.
    func towerOnChange<Value: Equatable>(
        of value: Value,
        initial: Bool = false,
        _ action: @escaping (Value, Value) -> Void
    ) -> some View {
        modifier(TowerChangeObserver(value: value, initial: initial, action: action))
    }
}

private struct TowerChangeObserver<Value: Equatable>: ViewModifier {
    let value: Value
    let initial: Bool
    let action: (Value, Value) -> Void

    @State private var previousValue: Value
    @State private var didDeliverInitial = false

    init(value: Value, initial: Bool, action: @escaping (Value, Value) -> Void) {
        self.value = value
        self.initial = initial
        self.action = action
        _previousValue = State(initialValue: value)
    }

    func body(content: Content) -> some View {
        content
            .onAppear {
                if initial && !didDeliverInitial {
                    didDeliverInitial = true
                    action(value, value)
                }
            }
            .onChange(of: value) { newValue in
                let oldValue = previousValue
                previousValue = newValue
                action(oldValue, newValue)
            }
    }
}

@MainActor
func towerWithAnimation(
    _ animation: Animation?,
    completionDelay: Duration = .milliseconds(450),
    _ updates: () -> Void,
    completion: @escaping @MainActor () -> Void
) {
    if #available(iOS 17.0, *) {
        withAnimation(animation, completionCriteria: .removed, updates, completion: completion)
    } else {
        withAnimation(animation, updates)
        Task { @MainActor in
            try? await Task.sleep(for: completionDelay)
            completion()
        }
    }
}
