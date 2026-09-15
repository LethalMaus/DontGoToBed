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
}
