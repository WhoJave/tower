import SwiftUI

// iOS 16 compatibility shims for APIs introduced by SwiftUI in iOS 17.
// iOS 17+ preserves the original haptic behavior; iOS 16 skips optional haptics.
enum TowerHaptic {
    case selection
    case success
}

extension View {
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
