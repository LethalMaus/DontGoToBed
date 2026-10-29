import XCTest

/// Opt-in controls for physical-device review capture. Does not alter game state.
final class ReviewAutomationTests: XCTestCase {
    private struct Step: Decodable {
        let action: String
        var label: String?
        var value: String?
        var seconds: Double?
        var direction: Double?
    }

    /// Native iPhone/iPad captures using the same debug-only starting scenes as Android.
    func testIdealMarketingScreens() throws {
        guard ProcessInfo.processInfo.environment["DGTB_IDEAL_CAPTURE"] == "1" else {
            throw XCTSkip("Opt-in marketing capture")
        }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "dev.jamescullimore.dontgotobed")
        let scenes: [(String, Int, String, String, Int)] = [
            ("house",13,"Ian","rugby",0),("arrows",9,"Leo","rock",0),
            ("portal",6,"Leo","adventure",0),("vine",2,"Leo","original",0),
            ("enemies",12,"Papa","mma",0),("mammy",11,"Ian","adventure",0),
            ("inventory",10,"Papa","adventure",0),("bed",0,"Leo","original",4)]
        func capture(_ name: String) {
            let image = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            image.name = "ideal-" + name; image.lifetime = .keepAlways; add(image)
        }
        for (name,scenario,player,outfit,bed) in scenes {
            if let only = ProcessInfo.processInfo.environment["DGTB_CAPTURE_SCENE"], name != only { continue }
            app.terminate(); XCUIDevice.shared.orientation = .landscapeLeft
            app.launchEnvironment = ["DGTB_IDEAL_CAPTURE":"1","DGTB_SCENARIO":String(scenario),
                "DGTB_PLAYER":player,"DGTB_OUTFIT":outfit,"DGTB_BED":String(bed),
                "DGTB_LANGUAGE_ID":ProcessInfo.processInfo.environment["DGTB_LANGUAGE_ID"] ?? "1"]
            app.launchArguments = ["-language","1"]
            app.launch()
            XCTAssertTrue(control(app,"Bag").waitForExistence(timeout:30), app.debugDescription)
            let specialName = player == "Leo" ? "Thunder drums" : (player == "Ian" ? "Hound rush" : "Papa power")
            let special = control(app,specialName)
            let jump = control(app,"Jump")
            XCTAssertTrue(special.exists && jump.exists)
            XCTAssertFalse(special.frame.intersects(jump.frame),"Special attack must not overlap Jump")
            if !["house","vine"].contains(name) { tap(app,"Zoom in") }
            if name == "inventory" || name == "arrows" {
                tap(app,"Bag")
                let item = localisedControl(name == "inventory" ? "Select Potion," : "Select Electric arrow,")
                let target = app.descendants(matching:.any).matching(NSPredicate(format:"label BEGINSWITH %@",item)).firstMatch
                XCTAssertTrue(target.waitForExistence(timeout:10),app.debugDescription); target.tap()
                if name == "arrows" { tap(app,"Done") }
            }
            if name == "vine" {
                let stick = control(app,"Move and aim")
                XCTAssertTrue(stick.waitForExistence(timeout:10),app.debugDescription)
                let centre = stick.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.5))
                centre.press(forDuration:0.05,thenDragTo:stick.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.02)),withVelocity:.fast,thenHoldForDuration:1.4)
                centre.press(forDuration:0.05,thenDragTo:stick.coordinate(withNormalizedOffset:CGVector(dx:0.98,dy:0.5)),withVelocity:.fast,thenHoldForDuration:0.8)
            }
            if ["house","portal","mammy","bed"].contains(name) { Thread.sleep(forTimeInterval:4) }
            capture(name)
            if name == "enemies" {
                for n in 0..<8 { Thread.sleep(forTimeInterval:0.5); capture("enemies-\(n)") }
            }
            if name == "arrows" {
                print("DGTB_ARROW_READY")
                fflush(stdout)
                Thread.sleep(forTimeInterval:5)
                for n in 0..<3 {
                    print("DGTB_ARROW_FIRE \(n) \(Date().timeIntervalSince1970)")
                    app.coordinate(withNormalizedOffset:CGVector(dx:0.70,dy:0.60)).tap()
                    capture("arrows-draw-\(n)")
                    Thread.sleep(forTimeInterval:0.6)
                }
                capture("arrows-fired")
            }
        }
        app.terminate()
    }

    /// Opt-in native Apple preview footage. Stages the same fixtures as Android, then uses real controls.
    func testApplePreviewFootage() throws {
        guard ProcessInfo.processInfo.environment["DGTB_APPLE_PREVIEW"] == "1" else { throw XCTSkip("Opt-in Apple preview capture") }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier:"dev.jamescullimore.dontgotobed")
        let language = ProcessInfo.processInfo.environment["DGTB_LANGUAGE_ID"] ?? "1"
        let selected = ProcessInfo.processInfo.environment["DGTB_PREVIEW_SCENES"]?.split(separator:",").map(String.init)
        let scenes:[(String,Int,String,String,Int)] = [("house",13,"Ian","rugby",0),("building",8,"Ian","rugby",0),("arrows",9,"Leo","rock",0),("vine",2,"Leo","original",0),("portal",6,"Leo","adventure",0),("mammy",11,"Ian","adventure",0),("potion",10,"Papa","adventure",0),("bed",0,"Leo","original",4)]
        func extra(_ en:String,_ de:String)->XCUIElement {
            let label = language == "7" ? de : en
            return app.descendants(matching:.any).matching(NSPredicate(format:"label == %@ OR label BEGINSWITH %@",label,label+",")).firstMatch
        }
        func move(_ x:Double,_ y:Double,_ duration:Double) {
            let stick = control(app,"Move and aim")
            stick.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.5)).press(forDuration:0.01,thenDragTo:stick.coordinate(withNormalizedOffset:CGVector(dx:x,dy:y)),withVelocity:.fast,thenHoldForDuration:duration)
        }
        func capture(_ name:String) {let a=XCTAttachment(screenshot:XCUIScreen.main.screenshot());a.name="preview-"+name;a.lifetime = .keepAlways;add(a)}
        for (scene,scenario,player,outfit,bed) in scenes {
            if let selected=selected, !selected.contains(scene) {continue}
            for attempt in 0..<10 {
                app.terminate();XCUIDevice.shared.orientation = .landscapeLeft
                app.launchEnvironment=["DGTB_IDEAL_CAPTURE":"1","DGTB_SCENARIO":String(scenario),"DGTB_PLAYER":player,"DGTB_OUTFIT":outfit,"DGTB_BED":String(bed),"DGTB_LANGUAGE_ID":language]
                app.launch();XCTAssertTrue(control(app,"Bag").waitForExistence(timeout:30),app.debugDescription)
                if !["house","vine"].contains(scene) {tap(app,"Zoom in")}
                if scene != "bed" || extra("Bed","Bett").frame.maxY > app.frame.height*0.70 {break}
                XCTAssertLessThan(attempt,9,"No suitable ground-level bed")
            }
            if ["potion","arrows"].contains(scene) {
                tap(app,"Bag")
                let item=localisedControl(scene == "potion" ? "Select Potion," : "Select Electric arrow,")
                let entry=app.descendants(matching:.any).matching(NSPredicate(format:"label BEGINSWITH %@",item)).firstMatch
                XCTAssertTrue(entry.waitForExistence(timeout:10));entry.tap();tap(app,"Done")
            }
            if scene == "portal" {move(0.02,0.5,0.45)}
            capture(scene+"-before")
            print("DGTB_PREVIEW_READY \(scene)");fflush(stdout);Thread.sleep(forTimeInterval:5)
            print("DGTB_PREVIEW_ACTION \(scene) \(Date().timeIntervalSince1970)");fflush(stdout)
            switch scene {
            case "house":Thread.sleep(forTimeInterval:4)
            case "building":move(0.98,0.5,0.10);extra("Place","Platzieren").tap();Thread.sleep(forTimeInterval:0.6);extra("Hit","Schlagen").press(forDuration:2);Thread.sleep(forTimeInterval:1)
            case "arrows":for _ in 0..<3 {app.coordinate(withNormalizedOffset:CGVector(dx:0.70,dy:0.60)).tap();Thread.sleep(forTimeInterval:0.7)}
            case "vine":move(0.5,0.02,1.4);move(0.98,0.5,0.8);move(0.02,0.5,0.8);Thread.sleep(forTimeInterval:1)
            case "portal":move(0.98,0.5,0.45);extra("Hit","Schlagen").tap();Thread.sleep(forTimeInterval:1.3);capture("portal-destination");extra("Hit","Schlagen").tap();Thread.sleep(forTimeInterval:1.3);capture("portal-returned");extra("Hit","Schlagen").tap();Thread.sleep(forTimeInterval:1.3);move(0.98,0.5,0.6)
            case "mammy":move(0.98,0.5,1.4);Thread.sleep(forTimeInterval:4)
            case "potion":extra("Use","Benutzen").tap();Thread.sleep(forTimeInterval:2.5)
                XCTAssertTrue(extra("Health: 3 of 5 hearts","Gesundheit: 3 von 5 Herzen").exists,"Healing not verified")
            case "bed":move(0.98,0.5,0.12);extra("Hit","Schlagen").press(forDuration:5);XCTAssertFalse(extra("Bed","Bett").exists,"Bed not destroyed");Thread.sleep(forTimeInterval:0.8);capture("bed-drops");move(0.98,0.5,0.6);Thread.sleep(forTimeInterval:1)
            default:break
            }
            Thread.sleep(forTimeInterval:1);capture(scene+"-after")
            print("DGTB_PREVIEW_DONE \(scene)");fflush(stdout);Thread.sleep(forTimeInterval:3)
        }
        app.terminate()
    }

    private func localisedControl(_ label: String) -> String {
        let id = Int(ProcessInfo.processInfo.environment["DGTB_LANGUAGE_ID"] ?? "1") ?? 1
        guard id >= 2 && id <= 7 else { return label }
        let names: [String: [String]] = [
            "Bag": ["Mochila", "Zaino", "Sac", "Mochila", "Рюкзак", "Rucksack"],
            "Thunder drums": ["Tambores de trueno", "Tamburi del tuono", "Tambours du tonnerre", "Tambores do trovão", "Громовые барабаны", "Donner-Trommeln"],
            "Hound rush": ["Ataque del sabueso", "Assalto del segugio", "Assaut du chien", "Ataque do sabujo", "Атака гончей", "Hundeansturm"],
            "Papa power": ["Poder de Papá", "Potenza di Papà", "Puissance de Papa", "Poder do Papai", "Сила папы", "Papas Kraft"],
            "Jump": ["Saltar", "Salta", "Sauter", "Pular", "Прыжок", "Springen"],
            "Zoom in": ["Acercar", "Aumenta zoom", "Zoomer", "Aumentar zoom", "Приблизить", "Vergrößern"],
            "Done": ["Listo", "Fatto", "Terminé", "Pronto", "Готово", "Fertig"],
            "Move and aim": ["Mover y apuntar", "Muoviti e mira", "Se déplacer et viser", "Mover e mirar", "Двигаться и целиться", "Bewegen und zielen"],
            "Select Potion,": ["Elegir Poción,", "Scegli Pozione,", "Choisir Potion,", "Escolher Poção,", "Выбрать: Зелье,", "Trank auswählen,"],
            "Select Electric arrow,": ["Elegir Flecha eléctrica,", "Scegli Freccia elettrica,", "Choisir Flèche électrique,", "Escolher Flecha elétrica,", "Выбрать: Электрическая стрела,", "Elektropfeil auswählen,"]
        ]
        return names[label]?[id - 2] ?? label
    }

    private func control(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        let label = localisedControl(label)
        return app.descendants(matching: .any).matching(NSPredicate(
            format: "label == %@ OR identifier == %@ OR label BEGINSWITH %@", label, label, label + ","
        )).firstMatch
    }

    private func tap(_ app: XCUIApplication, _ label: String) {
        let element = control(app, label)
        XCTAssertTrue(element.waitForExistence(timeout: 15), "Missing \(label): \(app.debugDescription)")
        if !element.isHittable { app.swipeUp() }
        XCTAssertTrue(element.isHittable, "Unreachable \(label): \(app.debugDescription)")
        element.tap()
    }

    /// Opt-in: run against another device hosting this build on the same LAN.
    func testNearbyGameJoin() throws {
        guard let name = ProcessInfo.processInfo.environment["DGTB_NEARBY_GAME"] else {
            throw XCTSkip("Requires a disposable LAN host")
        }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "dev.jamescullimore.dontgotobed")
        app.launchArguments = ["-language", "1"]
        XCUIDevice.shared.orientation = .landscapeLeft
        app.launch()
        tap(app, "Multiplayer"); tap(app, "Join")
        let permission = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        if permission.waitForExistence(timeout: 2) {
            let allow = permission.buttons["Allow"]
            if allow.exists { allow.tap() }
            else if permission.buttons["OK"].exists { permission.buttons["OK"].tap() }
        }
        // The dialog scrolls on short landscape screens; a host can be below the fold.
        Thread.sleep(forTimeInterval: 3)
        for _ in 0..<4 {
            if control(app, name).exists && control(app, name).isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(control(app, name).waitForExistence(timeout: 10), app.debugDescription)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "nearby-games"; attachment.lifetime = .keepAlways; add(attachment)
        tap(app, name)
        XCTAssertTrue(control(app, "Connected. Close this panel and join the adventure.").waitForExistence(timeout: 15), app.debugDescription)
        tap(app, "Done")
        XCTAssertTrue(control(app, "JOIN ADVENTURE").isEnabled)
        tap(app, "JOIN ADVENTURE")
        XCTAssertTrue(control(app, "Bag").waitForExistence(timeout: 20), app.debugDescription)
        tap(app, "Game menu"); tap(app, "Leave game")
        tap(app, "Multiplayer"); tap(app, "Host")
        let hosted = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH %@", "Nearby game: DGTB-")).firstMatch
        XCTAssertTrue(hosted.waitForExistence(timeout: 10), app.debugDescription)
        print("DGTB_DISCOVERY_HOST \(hosted.label)")
        // Leave time for the Android counterpart to find and join this host.
        Thread.sleep(forTimeInterval: 45)
        tap(app, "Solo")
    }

    func testLocalizedStoreScreens() throws {
        let env = ProcessInfo.processInfo.environment
        guard env["DGTB_LOCALIZED_CAPTURE"] == "1", let raw = env["DGTB_LOCALIZED_LABELS"],
              let locales = try JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [[String: String]] else {
            throw XCTSkip("Opt-in localized store capture")
        }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "dev.jamescullimore.dontgotobed")
        func capture(_ name: String) {
            let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
        }
        for locale in locales {
            let code = locale["code"]!
            app.terminate()
            XCUIDevice.shared.orientation = .landscapeLeft
            app.launchArguments = ["-AppleLanguages", "(\(code))", "-AppleLocale", code,
                "-language", locale["id"]!, "-selectedPlayer", "2", "-outfit_Papa", "3", "-buttonControls", "1"]
            app.launch()
            XCTAssertTrue(control(app, locale["Characters"]!).waitForExistence(timeout: 20), app.debugDescription)
            capture("\(code)-01-menu")
            tap(app, locale["Settings"]!); capture("\(code)-02-settings"); tap(app, locale["Done"]!)
            tap(app, locale["Worlds"]!); capture("\(code)-04-worlds"); tap(app, locale["Done"]!)
            tap(app, locale["Characters"]!); capture("\(code)-05-outfits"); tap(app, locale["Done"]!)
            tap(app, locale["LET'S PLAY"]!)
            XCTAssertTrue(control(app, locale["Bag"]!).waitForExistence(timeout: 20), app.debugDescription)
            // Use normal direction controls for repeatable terrain framing.
            for _ in 0..<3 { tap(app, locale["Step right"]!) }
            tap(app, locale["Turn the world"]!)
            for _ in 0..<2 { tap(app, locale["Step right"]!) }
            Thread.sleep(forTimeInterval: 3)
            capture("\(code)-06-gameplay")
            tap(app, locale["Bag"]!); capture("\(code)-07-inventory")
        }
    }

    func testReviewSequence() throws {
        let env = ProcessInfo.processInfo.environment
        guard env["DGTB_REVIEW_AUTOMATION"] == "1", let json = env["DGTB_REVIEW_STEPS"] else {
            throw XCTSkip("Physical review recording only")
        }
        continueAfterFailure = false
        let steps = try JSONDecoder().decode([Step].self, from: Data(json.utf8))
        let app = XCUIApplication(bundleIdentifier: "dev.jamescullimore.dontgotobed")
        for (index, step) in steps.enumerated() {
            print("DGTB_STEP \(index) \(step.action) \(step.label ?? "")")
            switch step.action {
            case "launch": app.launch()
            case "activate": app.activate()
            case "home": XCUIDevice.shared.press(.home)
            case "dismissPurchaseIfPresent":
                let close = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["dismiss"]
                if close.exists && close.isHittable { close.tap() }
            case "assertText":
                XCTAssertTrue(control(app, step.label!).waitForExistence(timeout: 15), app.debugDescription)
            case "assertPurchaseSheet":
                let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
                XCTAssertTrue(system.staticTexts["Small Tip"].waitForExistence(timeout: 15), system.debugDescription)
            case "watchPeerMovement":
                let peer = control(app, step.label ?? "Peer 0")
                XCTAssertTrue(peer.waitForExistence(timeout: 15), app.debugDescription)
                let deadline = Date().addingTimeInterval(step.seconds ?? 10)
                var positions: [CGFloat] = []
                while Date() < deadline {
                    if peer.exists { positions.append(peer.frame.minY) }
                    Thread.sleep(forTimeInterval: 0.25)
                }
                let distance = (positions.max() ?? 0) - (positions.min() ?? 0)
                print("DGTB_PEER_VERTICAL_MOVEMENT \(distance) points across \(positions.count) samples")
                XCTAssertGreaterThan(distance, 4, "Remote player did not visibly move while the local player was idle")
            case "tap": tap(app, step.label!)
            case "tapIfPresent":
                let element = control(app, step.label!)
                if element.exists && element.isHittable { element.tap() }
            case "wait": Thread.sleep(forTimeInterval: step.seconds ?? 1)
            case "swipeUp": app.swipeUp()
            case "swipeDown": app.swipeDown()
            case "type": app.typeText(step.value ?? "")
            case "done":
                let done = app.keyboards.buttons["Done"]
                if done.waitForExistence(timeout: 3) { done.tap() }
            case "move":
                let stick = control(app, "Move and aim")
                XCTAssertTrue(stick.waitForExistence(timeout: 10))
                let start = stick.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                let end = stick.coordinate(withNormalizedOffset: CGVector(dx: (step.direction ?? 1) > 0 ? 0.95 : 0.05, dy: 0.5))
                start.press(forDuration: 0.01, thenDragTo: end, withVelocity: .fast, thenHoldForDuration: step.seconds ?? 0.2)
            case "solveGate":
                let field = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "multiplied by")).firstMatch
                XCTAssertTrue(field.waitForExistence(timeout: 10))
                let labels = app.descendants(matching: .any).allElementsBoundByIndex.map { $0.label }.joined(separator: "\n")
                let expression = try NSRegularExpression(pattern: "What is ([0-9]+) multiplied by ([0-9]+)\\?")
                guard let match = expression.firstMatch(in: labels, range: NSRange(labels.startIndex..., in: labels)),
                      let a = Range(match.range(at: 1), in: labels), let b = Range(match.range(at: 2), in: labels),
                      let first = Int(labels[a]), let second = Int(labels[b]) else {
                    XCTFail("Could not read grown-ups gate"); return
                }
                field.tap()
                app.typeText(String(first * second))
                let done = app.keyboards.buttons["Done"]
                XCTAssertTrue(done.waitForExistence(timeout: 5))
                done.tap()
                app.swipeUp()
                tap(app, "Continue")
            case "tip":
                let tip = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Leave a tip")).firstMatch
                XCTAssertTrue(tip.waitForExistence(timeout: 45), app.debugDescription)
                XCTAssertTrue(tip.isEnabled, "Tip disabled: \(app.debugDescription)")
                if !tip.isHittable { app.swipeUp() }
                tip.tap()
            case "dump":
                print("DGTB_REVIEW_UI_BEGIN\n\(app.debugDescription)\nDGTB_REVIEW_UI_END")
                print("DGTB_SYSTEM_UI_BEGIN\n\(XCUIApplication(bundleIdentifier: "com.apple.springboard").debugDescription)\nDGTB_SYSTEM_UI_END")
                let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
                attachment.name = step.label ?? "review-\(index)"
                attachment.lifetime = .keepAlways
                add(attachment)
            default: XCTFail("Unknown review action: \(step.action)")
            }
        }
    }

    override class func setUp() {
        super.setUp()
        if ProcessInfo.processInfo.environment["DGTB_REVIEW_PREWARM"] == "1" {
            XCUIDevice.shared.orientation = .landscapeLeft
            XCUIApplication(bundleIdentifier: "dev.jamescullimore.dontgotobed").activate()
            Thread.sleep(forTimeInterval: 2)
        }
    }

    func testGameplayCaptureProbe() throws {
        guard ProcessInfo.processInfo.environment["DGTB_REVIEW_AUTOMATION"] == "1" else {
            throw XCTSkip("Physical review recording only")
        }
        let app = XCUIApplication(bundleIdentifier: "dev.jamescullimore.dontgotobed")
        Thread.sleep(forTimeInterval: 2)
        let help = app.buttons["How to play"]
        XCTAssertTrue(help.waitForExistence(timeout: 10))
        help.tap()
        Thread.sleep(forTimeInterval: 4)
        app.buttons["Let's play"].tap()
        Thread.sleep(forTimeInterval: 3)
    }

    func testCaptureProbe() throws {
        guard ProcessInfo.processInfo.environment["DGTB_REVIEW_AUTOMATION"] == "1" else {
            throw XCTSkip("Only run when preparing a physical-device review recording")
        }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "dev.jamescullimore.dontgotobed")
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1)
        app.activate()
        Thread.sleep(forTimeInterval: 3)
        print("DGTB_REVIEW_UI_BEGIN\n\(app.debugDescription)\nDGTB_REVIEW_UI_END")
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "review-capture-probe"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

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

    /// Regression: the landscape Join field must let a player dismiss the keyboard.
    func testJoinKeyboardDismissal() {
        tap("Join")
        let address = control("Enter Host IP")
        XCTAssertTrue(address.waitForExistence(timeout: 15))
        app.swipeUp()
        address.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        app.typeText("192.0.2.1")
        capture("join-keyboard-visible")
        let done = app.keyboards.buttons["Done"]
        XCTAssertTrue(done.waitForExistence(timeout: 5), app.debugDescription)
        done.tap()
        let hidden = NSPredicate(format: "exists == false")
        expectation(for: hidden, evaluatedWith: app.keyboards.firstMatch)
        waitForExpectations(timeout: 10)
        app.swipeUp()
        let connect = control("Test Connection")
        XCTAssertTrue(connect.isHittable, app.debugDescription)
        XCTAssertTrue(connect.isEnabled)
        capture("join-keyboard-dismissed")
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
