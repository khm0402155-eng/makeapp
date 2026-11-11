# GIF 생성기

[gifforfree](https://khm0402155-eng.github.io/gifforfree/) 를 전체 화면으로 여는 안드로이드 앱입니다. 앱 안에는 주소창이 없고, 동영상 선택과 GIF 저장은 휴대폰에서 동작합니다.

## 설치 파일 받기

이 폴더를 GitHub 저장소에 올린 뒤 `main` 또는 `master` 브랜치로 푸시하면 Actions가 APK를 만듭니다. Actions 탭에서 **Build APK** 를 직접 실행할 수도 있습니다.

1. 저장소를 GitHub에 만듭니다.
2. 이 폴더 전체를 푸시합니다.
3. 저장소의 **Actions** 탭에서 빌드가 끝날 때까지 기다립니다.
4. 완료된 실행을 열고 **gifforfree-apk** 아티팩트를 받습니다.
5. 압축을 풀면 `app-release.apk` 가 있습니다. 휴대폰으로 옮겨 설치합니다. Android 8 이상이 필요합니다.

서명 키를 저장소 비밀로 넣지 않으면, 설치 가능한 APK를 디버그 키로 서명합니다. 폰에 업데이트로 덮어쓰려면 매번 같은 키가 필요하므로, 그때는 아래 비밀을 등록합니다.

| 비밀 이름 | 값 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore 파일을 base64로 만든 문자열 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 비밀번호 |
| `ANDROID_KEY_ALIAS` | 키 별칭 |
| `ANDROID_KEY_PASSWORD` | 키 비밀번호 |

## 앱에서 되는 일

- 사이트 화면이 앱 전체를 채웁니다.
- 동영상 파일을 고르면 사이트의 파일 선택으로 전달됩니다.
- 변환이 끝난 뒤 저장하면 GIF가 다운로드 폴더에 들어갑니다.
- 뒤로 가면 사이트 이전 화면으로 돌아가고, 더 없으면 앱이 닫힙니다.
