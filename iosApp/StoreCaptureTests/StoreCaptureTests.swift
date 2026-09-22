import XCTest

/// Drives the ordinary app through real controls; no game-state or rendering fixtures.
/// Attachments are candidates for review, not automatic claims of store readiness.
final class StoreCaptureTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .landscapeLeft
        app.launch()
    }

    private func control(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR identifier == %@ OR label BEGINSWITH %@", label, label, label + ",")).firstMatch
    }

    private func tap(_ label: String) {
        let element = control(label)
        XCTAssertTrue(element.waitForExistence(timeout: 15), "Missing control: \(label)\n\(app.debugDescription)")
        element.tap()
    }

    private func capture(_ name: String) {
        let image = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        image.name = name
        image.lifetime = .keepAlways
        add(image)
    }

    private func marketingCapture(_ name: String) {
        // Request a fresh layout and let the system rotation animation finish
        // before taking a screenshot of the stable landscape UI.
        XCUIDevice.shared.orientation = .landscapeRight
        Thread.sleep(forTimeInterval: 2)
        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 2)
        capture(name)
    }

    private func moveRight() {
        let stick = control("Move and aim")
        XCTAssertTrue(stick.waitForExistence(timeout: 10))
        let start = stick.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let end = stick.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 1.0)
    }

    /// A separate recording session: screenshot smoke tests terminate too soon for Instruments.
    func testSustainedGameplayRecording() {
        XCTAssertTrue(control("How to play").waitForExistence(timeout: 30))
        let leo = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "Leo")).firstMatch
        XCTAssertTrue(leo.waitForExistence(timeout: 10))
        leo.tap()
        XCTAssertTrue(control("Jump").waitForExistence(timeout: 30))
        moveRight()
        tap("Jump")
        Thread.sleep(forTimeInterval: 1)
        capture("recording-ready")
        print("DGTB_RECORDING_READY")
        // Keep this app alive while the external recorder initializes and records up to 45 seconds.
        // Timed enemy waves continue normally; no test fixture changes the game state.
        for _ in 0..<90 { Thread.sleep(forTimeInterval: 1) }
        XCTAssertEqual(app.state, .runningForeground)
        capture("recording-finished")
    }

    func testStoreScreenshots() {
        XCTAssertTrue(control("How to play").waitForExistence(timeout: 30))
        capture("01-character-selection")
        // Compose can merge the character image and text into a repeated label.
        let leo = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "Leo")).firstMatch
        if leo.waitForExistence(timeout: 5) { leo.tap() } else { tap("Leo") }
        XCTAssertTrue(control("Jump").waitForExistence(timeout: 30))
        Thread.sleep(forTimeInterval: 1)
        capture("02-explore")
        moveRight()
        tap("Jump")
        Thread.sleep(forTimeInterval: 0.25)
        capture("03-jump")
        Thread.sleep(forTimeInterval: 1)
        tap("Place")
        Thread.sleep(forTimeInterval: 0.4)
        capture("04-build")
        moveRight()
        tap("Jump")
        Thread.sleep(forTimeInterval: 1)
        tap("Switch latitude longitude")
        Thread.sleep(forTimeInterval: 1)
        capture("05-turn-the-world")
        tap("Bag")
        capture("06-inventory")
        tap("Close")
        for _ in 0..<4 { tap("Hit"); Thread.sleep(forTimeInterval: 0.45) }
        capture("07-gameplay")
    }

    /// Marketing sources: build a visible route through normal controls.
    /// Keep the original full-screen attachments as provenance for designed exports.
    func testMarketingScreenshots() {
        XCTAssertTrue(control("How to play").waitForExistence(timeout: 30))
        capture("01-characters")
        let leo = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "Leo")).firstMatch
        XCTAssertTrue(leo.waitForExistence(timeout: 10))
        leo.tap()
        XCTAssertTrue(control("Jump").waitForExistence(timeout: 30))
        Thread.sleep(forTimeInterval: 2)
        capture("02-adventure")
        let stick = control("Move and aim")
        for step in 1...3 {
            tap("Place")
            tap("Jump")
            let start = stick.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            let end = stick.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5))
            start.press(forDuration: 0.01, thenDragTo: end, withVelocity: .fast, thenHoldForDuration: 0.12)
            Thread.sleep(forTimeInterval: 0.5)
            capture("03-build-step-\(step)")
        }
        Thread.sleep(forTimeInterval: 3)
        marketingCapture("04-built-route")
        tap("Switch latitude longitude")
        Thread.sleep(forTimeInterval: 3)
        marketingCapture("05-new-direction")
        for _ in 0..<3 { moveRight(); tap("Jump") }
        capture("06-exploration")
    }

    /// Requires a real Android host exposed on localhost:8082 with adb forward.
    /// The simulator joins as a normal client; no player or world is fabricated.
    func testMarketingMultiplayer() throws {
        guard ProcessInfo.processInfo.environment["DGTB_CAPTURE_MULTIPLAYER"] == "1" else {
            throw XCTSkip("Set TEST_RUNNER_DGTB_CAPTURE_MULTIPLAYER=1 with a real host on localhost:8082")
        }
        XCTAssertTrue(control("How to play").waitForExistence(timeout: 30))
        tap("Join")
        let address = control("Enter Host IP")
        XCTAssertTrue(address.waitForExistence(timeout: 10))
        address.tap()
        app.typeText("127.0.0.1")
        // Keep the controls above the software keyboard in landscape.
        let scrollStart = app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.52))
        let scrollEnd = app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.12))
        scrollStart.press(forDuration: 0.01, thenDragTo: scrollEnd)
        tap("Test Connection")
        scrollStart.press(forDuration: 0.01, thenDragTo: scrollEnd)
        let ian = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", "Ian")).firstMatch
        XCTAssertTrue(ian.waitForExistence(timeout: 15))
        ian.tap()
        XCTAssertTrue(control("Jump").waitForExistence(timeout: 30))
        Thread.sleep(forTimeInterval: 2)
        marketingCapture("07-together")
        tap("Jump")
        Thread.sleep(forTimeInterval: 0.2)
        capture("08-together-jump")
    }

    /// Review evidence from the actual Release purchase flow; never initiates a purchase.
    func testPurchaseReviewScreenshots() throws {
        tap("Grown-ups")
        let field = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "multiplied by")).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 15), app.debugDescription)
        let labels = app.descendants(matching: .any).allElementsBoundByIndex.map { $0.label }.joined(separator: "\n")
        let expression = try NSRegularExpression(pattern: "What is ([0-9]+) multiplied by ([0-9]+)\\?")
        let range = NSRange(labels.startIndex..., in: labels)
        guard let match = expression.firstMatch(in: labels, range: range),
              let firstRange = Range(match.range(at: 1), in: labels),
              let secondRange = Range(match.range(at: 2), in: labels),
              let first = Int(labels[firstRange]), let second = Int(labels[secondRange]) else {
            capture("90-gate-diagnostic")
            XCTFail("Could not read the ordinary arithmetic gate: \(labels)")
            return
        }
        field.tap()
        app.typeText(String(first * second))
        let scrollStart = app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.43))
        let scrollEnd = app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.12))
        scrollStart.press(forDuration: 0.01, thenDragTo: scrollEnd)
        tap("Continue")
        let tip = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Leave a tip")).firstMatch
        let loaded = tip.waitForExistence(timeout: 60)
        capture("91-support-diagnostic")
        guard loaded else {
            XCTFail("Store prices did not load: \(app.debugDescription)")
            return
        }
        // Scroll normally to show the purchase choices without changing app state.
        app.swipeUp()
        Thread.sleep(forTimeInterval: 2)
        capture("01-tip-options")
        app.swipeUp()
        Thread.sleep(forTimeInterval: 2)
        capture("02-tip-options-lower")
    }
}
