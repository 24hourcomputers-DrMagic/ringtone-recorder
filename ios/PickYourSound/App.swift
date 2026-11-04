import SwiftUI
import AVFoundation

@main
struct PickYourSoundApp: App {
    var body: some Scene { WindowGroup { ContentView() } }
}

final class AudioModel: NSObject, ObservableObject, AVAudioRecorderDelegate {
    @Published var isRecording = false
    @Published var duration: Double = 0
    @Published var start: Double = 0
    @Published var end: Double = 0
    @Published var message: String?
    @Published var exportURL: URL?
    private var recorder: AVAudioRecorder?
    private var player: AVAudioPlayer?
    private let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    var rawURL: URL { docs.appendingPathComponent("recording.m4a") }

    func record() {
        AVAudioSession.sharedInstance().requestRecordPermission { ok in
            DispatchQueue.main.async {
                guard ok else { self.message = NSLocalizedString("mic_denied", comment: ""); return }
                self.beginRecording()
            }
        }
    }

    private func beginRecording() {
        do {
            let s = AVAudioSession.sharedInstance()
            try s.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try s.setActive(true)
            let settings: [String: Any] = [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 44100,
                                           AVNumberOfChannelsKey: 1, AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue]
            recorder = try AVAudioRecorder(url: rawURL, settings: settings)
            recorder?.delegate = self
            recorder?.record()
            isRecording = true
        } catch { message = error.localizedDescription }
    }

    func stop() {
        recorder?.stop(); isRecording = false
        let d = CMTimeGetSeconds(AVURLAsset(url: rawURL).duration)
        duration = d.isFinite ? d : 0
        start = 0; end = min(duration, 30)
    }

    /// Export [start, end] as .m4r (AAC in MPEG-4, the iOS ringtone format). Ringtones max 30 s.
    func exportClip(completion: @escaping (URL?) -> Void) {
        let asset = AVURLAsset(url: rawURL)
        guard let ex = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetAppleM4A) else { completion(nil); return }
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("clip.m4a")
        try? FileManager.default.removeItem(at: tmp)
        ex.outputURL = tmp; ex.outputFileType = .m4a
        let e = min(end, start + 30)
        ex.timeRange = CMTimeRange(start: CMTime(seconds: start, preferredTimescale: 600),
                                   end: CMTime(seconds: e, preferredTimescale: 600))
        ex.exportAsynchronously {
            let out = FileManager.default.temporaryDirectory.appendingPathComponent("Pick Your Sound.m4r")
            try? FileManager.default.removeItem(at: out)
            let ok = ex.status == .completed && (try? FileManager.default.moveItem(at: tmp, to: out)) != nil
            DispatchQueue.main.async { completion(ok ? out : nil) }
        }
    }

    func preview() {
        exportClip { url in
            guard let url else { return }
            try? AVAudioSession.sharedInstance().setCategory(.playback)
            self.player = try? AVAudioPlayer(contentsOf: url)
            self.player?.play()
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

struct ContentView: View {
    @StateObject private var m = AudioModel()
    @State private var share: URL?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("Ringtone Pick Your Sound").font(.largeTitle.bold()).multilineTextAlignment(.center)
                Text("tagline").font(.title3).foregroundStyle(.secondary)
                Label("mic_only_note", systemImage: "mic.fill").font(.footnote)
                HStack {
                    Button { m.record() } label: { Label("record", systemImage: "record.circle") }
                        .buttonStyle(.borderedProminent).disabled(m.isRecording)
                    Button { m.stop() } label: { Label("stop", systemImage: "stop.circle") }
                        .buttonStyle(.bordered).disabled(!m.isRecording)
                }
                if m.duration > 0 {
                    Text(String(format: NSLocalizedString("recorded", comment: ""), m.duration))
                    VStack(alignment: .leading) {
                        Text("start"); Slider(value: $m.start, in: 0...m.duration, onEditingChanged: { _ in if m.start > m.end { m.end = m.start } })
                        Text("end"); Slider(value: $m.end, in: 0...m.duration, onEditingChanged: { _ in if m.end < m.start { m.start = m.end } })
                        Text(String(format: NSLocalizedString("clip", comment: ""), m.start, min(m.end, m.start + 30)))
                            .font(.caption)
                    }
                    HStack {
                        Button("preview") { m.preview() }.buttonStyle(.bordered)
                        Button("export") { m.exportClip { share = $0 } }.buttonStyle(.borderedProminent)
                    }
                    GroupBox(label: Text("howto_title")) { Text("howto_body").font(.footnote) }
                }
                if let msg = m.message { Text(msg).foregroundStyle(.red) }
            }.padding()
        }
        .sheet(item: $share) { ShareSheet(url: $0) }
    }
}

extension URL: Identifiable { public var id: String { absoluteString } }
