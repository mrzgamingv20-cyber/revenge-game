#pragma once
#include <string>
#include <vector>
#include <unordered_map>

// Satu pilihan dialog: teks tombol, node tujuan, dan efeknya ke skor humanity
struct Choice {
    std::string text;
    int targetId;
    int humanityDelta;
};

// Satu node cerita (satu "layar" dialog)
struct StoryNode {
    int id;
    std::string text;
    std::vector<Choice> choices; // kosong = node akhir (ending)
};

class StoryEngine {
public:
    StoryEngine();

    // Kembalikan node saat ini dalam bentuk JSON mentah (di-parse di sisi Kotlin)
    std::string getCurrentNodeJson();

    // Ambil pilihan ke-index dari node saat ini, pindah ke node berikutnya
    void choose(int choiceIndex);

    // Reset total, mulai cerita dari awal lagi
    void reset();

    bool isEnding() const;

private:
    int currentId;
    int humanity;
    std::unordered_map<int, StoryNode> nodes;

    void buildStory();
    std::string escapeJson(const std::string& s);
    std::string buildEndingText(); // menentukan ending final berdasar skor humanity
};
