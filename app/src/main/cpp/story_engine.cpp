#include "story_engine.h"
#include <sstream>

// id 999 dipakai sebagai penanda "node akhir, hitung ending sekarang"
static const int FINAL_GATE_ID = 999;

StoryEngine::StoryEngine() : currentId(0), humanity(0) {
    buildStory();
}

void StoryEngine::reset() {
    currentId = 0;
    humanity = 0;
}

bool StoryEngine::isEnding() const {
    return currentId == FINAL_GATE_ID;
}

void StoryEngine::buildStory() {
    nodes.clear();

    nodes[0] = StoryNode{
        0,
        "Malam itu kamu dapat kabar: adikmu tewas dalam 'kecelakaan kerja' "
        "di pabrik milik Handoko. Tapi kamu tahu itu bukan kecelakaan — "
        "laporan itu ditutup terlalu cepat, terlalu rapi.\n\n"
        "Kamu mulai mencari tahu siapa Handoko sebenarnya.",
        {
            {"Kumpulkan bukti dulu, jangan gegabah", 1, +2},
            {"Langsung cari cara untuk menghadapinya", 1, -2},
        }
    };

    nodes[1] = StoryNode{
        1,
        "Setelah berhari-hari menelusuri, kamu menemukan alamat rumah Handoko. "
        "Dari jendela, kamu melihatnya duduk sendirian, tampak lelah — bukan seperti "
        "sosok jahat yang kamu bayangkan.",
        {
            {"Ketuk pintu, hadapi dia secara langsung", 2, +1},
            {"Masuk diam-diam, mengendap dari belakang", 2, -3},
        }
    };

    nodes[2] = StoryNode{
        2,
        "Handoko kaget melihatmu. Sebelum kamu sempat bicara, dia justru menangis: "
        "'Aku tahu kamu akan datang. Adikmu... dia melihat aku menyembunyikan laporan "
        "korupsi anggaran keselamatan kerja. Aku panik, aku menyuruh orang menakut-nakutinya "
        "saja — tapi jadi berujung maut. Aku pengecut. Aku tidak pernah berniat itu terjadi.'",
        {
            {"Maafkan dia — dendam tidak akan menghidupkan adikmu", 999, +4},
            {"Serahkan dia ke polisi bersama bukti-buktimu", 999, +2},
            {"Habisi dia sekarang juga, seperti dia menghabisi adikmu", 999, -5},
        }
    };

    // Node placeholder, teksnya diisi dinamis lewat buildEndingText()
    nodes[FINAL_GATE_ID] = StoryNode{ FINAL_GATE_ID, "", {} };
}

std::string StoryEngine::buildEndingText() {
    if (humanity >= 6) {
        return "ENDING: DAMAI\n\n"
               "Kamu memilih memaafkan. Bukan karena Handoko pantas dimaafkan, "
               "tapi karena kamu menolak membiarkan kebencian mengambil alih sisa hidupmu. "
               "Handoko menyerahkan diri sendiri ke polisi keesokan harinya. "
               "Kamu pulang membawa luka, tapi bukan dosa baru.\n\n"
               "-- Selesai --";
    } else if (humanity >= 0) {
        return "ENDING: KEADILAN\n\n"
               "Kamu tidak membalas dendam dengan tanganmu sendiri — kamu membiarkan hukum "
               "yang bekerja. Handoko diadili. Rasanya tidak sepenuhnya lega, tidak sepenuhnya "
               "puas, tapi setidaknya kamu masih mengenali dirimu sendiri di cermin.\n\n"
               "-- Selesai --";
    } else {
        return "ENDING: LINGKARAN DENDAM\n\n"
               "Kamu membalasnya dengan kekerasan yang sama seperti yang merenggut adikmu. "
               "Untuk sesaat kamu merasa menang. Tapi di malam-malam berikutnya, kamu sadar "
               "kamu sudah menjadi bagian dari hal yang paling kamu benci.\n\n"
               "-- Selesai --";
    }
}

std::string StoryEngine::escapeJson(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': break;
            default: out += c;
        }
    }
    return out;
}

std::string StoryEngine::getCurrentNodeJson() {
    std::ostringstream json;
    bool ending = isEnding();
    std::string text = ending ? buildEndingText() : nodes[currentId].text;

    json << "{";
    json << "\"id\":" << currentId << ",";
    json << "\"humanity\":" << humanity << ",";
    json << "\"isEnding\":" << (ending ? "true" : "false") << ",";
    json << "\"text\":\"" << escapeJson(text) << "\",";
    json << "\"choices\":[";

    if (!ending) {
        const auto& choices = nodes[currentId].choices;
        for (size_t i = 0; i < choices.size(); ++i) {
            if (i > 0) json << ",";
            json << "{\"text\":\"" << escapeJson(choices[i].text) << "\"}";
        }
    }

    json << "]}";
    return json.str();
}

void StoryEngine::choose(int choiceIndex) {
    if (isEnding()) return; // tidak ada pilihan di layar ending

    auto& node = nodes[currentId];
    if (choiceIndex < 0 || choiceIndex >= (int) node.choices.size()) return;

    const Choice& c = node.choices[choiceIndex];
    humanity += c.humanityDelta;
    currentId = c.targetId;
}
